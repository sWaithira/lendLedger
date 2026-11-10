package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.lendLedger.backend.audit.AuditService;
import com.lendLedger.backend.common.ApiException;
import com.lendLedger.backend.common.TenantClock;
import com.lendLedger.backend.finance.Allocation;
import com.lendLedger.backend.finance.AllocationEngine;
import com.lendLedger.backend.finance.AssessedCharge;
import com.lendLedger.backend.finance.BalanceComponent;
import com.lendLedger.backend.finance.ChargeAssessor;
import com.lendLedger.backend.finance.ChargeType;
import com.lendLedger.backend.finance.LedgerBalance;
import com.lendLedger.backend.finance.LoanTerms;
import com.lendLedger.backend.finance.OverpaymentException;
import com.lendLedger.backend.finance.Outstanding;
import com.lendLedger.backend.finance.PeriodKind;
import com.lendLedger.backend.security.CurrentUser;
import com.lendLedger.backend.security.LendLedgerPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepaymentService {

    private static final int MIN_KEY_LENGTH = 8;
    private static final int MAX_KEY_LENGTH = 100;

    private record Plan(List<AssessedCharge> charges, Outstanding before, List<Allocation> allocations, Outstanding after) { }

    private record Raw(LocalDate date, int rank, long tie, String type, String description,
                       BigDecimal signedAmount, UUID repaymentId, Instant at) { }

    private final LoanRepository loans;
    private final RepaymentRepository repayments;
    private final LoanService loanService;
    private final AuditService audit;
    private final TenantClock clock;

    public RepaymentService(LoanRepository loans, RepaymentRepository repayments, LoanService loanService,
                            AuditService audit, TenantClock clock) {
        this.loans = loans;
        this.repayments = repayments;
        this.loanService = loanService;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ preview

    /** Shows how a payment would be split, without saving anything. */
    @Transactional(readOnly = true)
    public RepaymentDtos.RepaymentPreview preview(UUID loanId, RepaymentDtos.PreviewRequest request) {
        UUID tenantId = CurrentUser.tenantId();
        LoanRow loan = loans.findById(tenantId, loanId).orElseThrow(RepaymentService::loanNotFound);
        requireOpen(loan);
        validateDate(tenantId, loan, request.paymentDate());
        Plan plan = plan(tenantId, loan, request.amount(), request.paymentDate());
        return new RepaymentDtos.RepaymentPreview(request.paymentDate(), request.amount(), balance(plan.before()),
                lines(plan.allocations()), balance(plan.after()), plan.after().isSettled());
    }

    // ------------------------------------------------------------------ post

    @Transactional
    public RepaymentDtos.PostResult post(UUID loanId, String idempotencyKey, RepaymentDtos.PostRequest request) {
        LendLedgerPrincipal user = CurrentUser.require();
        UUID tenantId = user.tenantId();
        String key = requireKey(idempotencyKey);
        String reference = blankToNull(request.reference());

        // 1. Lock the loan. Concurrent payments for this loan queue here and run one at a time (ADR-03).
        LoanRow loan = loans.lockById(tenantId, loanId).orElseThrow(RepaymentService::loanNotFound);

        // 2. Idempotency check AFTER the lock, so a retry that raced the original sees the committed original.
        String hash = RequestHash.of(loanId.toString(), request.amount().setScale(2).toPlainString(),
                request.paymentDate().toString(), request.method().name(), reference);
        Optional<RepaymentRow> existing = repayments.findByIdempotencyKey(tenantId, key);
        if (existing.isPresent()) {
            if (!existing.get().requestHash().equals(hash)) {
                throw new ApiException(HttpStatus.CONFLICT, "idempotency_key_reused",
                        "This Idempotency-Key was already used for a different request");
            }
            return new RepaymentDtos.PostResult(view(tenantId, existing.get()), loanService.get(loanId), true);
        }

        // 3. Validate and calculate (nothing is written yet).
        requireOpen(loan);
        validateDate(tenantId, loan, request.paymentDate());
        Plan plan = plan(tenantId, loan, request.amount(), request.paymentDate());

        // 4. Write: accrued charges, the payment, its allocations, settlement, audit. All or nothing.
        UUID ruleVersionId = loan.ruleVersionId();
        for (AssessedCharge c : plan.charges()) {
            loans.insertCharge(tenantId, loanId, c.type().name(), c.kind().name(), c.periodNumber(),
                    c.effectiveDate(), c.amount(), ruleVersionId);
        }
        UUID repaymentId = UUID.randomUUID();
        repayments.insertRepayment(repaymentId, tenantId, loanId, request.amount(), loan.currency(),
                request.paymentDate(), request.method().name(), reference, key, hash, user.userId());
        int sequence = 1;
        for (Allocation a : plan.allocations()) {
            repayments.insertAllocation(tenantId, repaymentId, loanId, a.component().name(), a.amount(), sequence++);
        }
        boolean settled = plan.after().isSettled();
        if (settled) loans.markSettled(tenantId, loanId, request.paymentDate());

        audit.record("REPAYMENT_POSTED", "REPAYMENT", repaymentId, null, Map.of(
                "loanId", loanId.toString(),
                "amount", request.amount().toPlainString(),
                "settledLoan", String.valueOf(settled)));

        RepaymentRow saved = repayments.findById(tenantId, repaymentId).orElseThrow();
        return new RepaymentDtos.PostResult(view(tenantId, saved), loanService.get(loanId), false);
    }

    // ------------------------------------------------------------------ reverse

    /** Reverses the latest payment of a loan. The original rows stay; mirror rows cancel them out (ADR-07). */
    @Transactional
    public RepaymentDtos.ReverseResult reverse(UUID repaymentId, String reason) {
        LendLedgerPrincipal user = CurrentUser.require();
        UUID tenantId = user.tenantId();

        RepaymentRow found = repayments.findById(tenantId, repaymentId).orElseThrow(RepaymentService::repaymentNotFound);
        LoanRow loan = loans.lockById(tenantId, found.loanId()).orElseThrow(RepaymentService::loanNotFound);

        // Re-read after the lock, in case another request reversed it while we waited.
        RepaymentRow current = repayments.findById(tenantId, repaymentId).orElseThrow(RepaymentService::repaymentNotFound);
        if (current.reversedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "already_reversed", "This payment has already been reversed");
        }
        if ("ROLLOVER".equals(current.method())) {
            throw new ApiException(HttpStatus.CONFLICT, "rollover_not_reversible",
                    "A rollover cannot be reversed because a new loan was created from it");
        }
        UUID latest = repayments.latestActiveRepaymentId(tenantId, loan.id()).orElse(null);
        if (!repaymentId.equals(latest)) {
            throw new ApiException(HttpStatus.CONFLICT, "only_latest_reversible",
                    "Only the most recent payment on a loan can be reversed. Reverse the later payments first.");
        }

        String cleanReason = reason.trim();
        UUID reversalId = UUID.randomUUID();
        repayments.insertReversal(reversalId, tenantId, repaymentId, loan.id(), cleanReason, user.userId());
        repayments.insertMirrorAllocations(tenantId, repaymentId, reversalId);
        if ("COMPLETED".equals(loan.status())) loans.reopen(tenantId, loan.id());

        audit.record("REPAYMENT_REVERSED", "REPAYMENT", repaymentId, cleanReason, Map.of(
                "loanId", loan.id().toString(),
                "amount", current.amount().toPlainString()));

        RepaymentRow reversed = repayments.findById(tenantId, repaymentId).orElseThrow();
        return new RepaymentDtos.ReverseResult(view(tenantId, reversed), loanService.get(loan.id()));
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<RepaymentDtos.RepaymentView> list(UUID loanId) {
        UUID tenantId = CurrentUser.tenantId();
        loanService.get(loanId);   // 404 if the loan is missing or belongs to another tenant
        return views(tenantId, repayments.listByLoan(tenantId, loanId));
    }

    /** Every event on the loan in date order with a running balance that ends at the current outstanding total. */
    @Transactional(readOnly = true)
    public RepaymentDtos.Statement statement(UUID loanId) {
        UUID tenantId = CurrentUser.tenantId();
        LoanDtos.LoanView view = loanService.get(loanId);
        LoanRow loan = loans.findById(tenantId, loanId).orElseThrow(RepaymentService::loanNotFound);
        LoanTerms terms = loan.terms();

        List<Raw> raw = new ArrayList<>();
        raw.add(new Raw(loan.issueDate(), 0, 0, "LOAN_ISSUED", "Loan issued", loan.principal(), null, null));

        for (AssessedCharge c : ChargeAssessor.assess(terms, view.asOf())) {
            String what = c.type() == ChargeType.INTEREST ? "Interest" : "Penalty";
            String when = c.kind() == PeriodKind.TERM ? "term" : "overdue";
            long tie = c.periodNumber() * 2L + (c.type() == ChargeType.PENALTY ? 1 : 0);
            raw.add(new Raw(c.effectiveDate(), 1, tie, c.type().name(),
                    what + ", " + when + " period " + c.periodNumber(), c.amount(), null, null));
        }

        List<RepaymentRow> rows = repayments.listByLoan(tenantId, loanId);
        Map<UUID, List<RepaymentDtos.AllocationLine>> allocations = allocationsByRepayment(tenantId, rows);
        long tie = 0;
        for (RepaymentRow r : rows) {
            String split = allocations.getOrDefault(r.id(), List.of()).stream()
                    .map(a -> a.component() + " " + a.amount().toPlainString())
                    .collect(Collectors.joining(", "));
            raw.add(new Raw(r.paymentDate(), 2, tie++, "PAYMENT",
                    "Payment by " + r.method() + (split.isEmpty() ? "" : " (" + split + ")"),
                    r.amount().negate(), r.id(), r.recordedAt()));
            if (r.reversedAt() != null) {
                LocalDate reversedOn = r.reversedAt().atZone(clock.zone()).toLocalDate();
                if (reversedOn.isBefore(r.paymentDate())) reversedOn = r.paymentDate();
                raw.add(new Raw(reversedOn, 3, tie++, "REVERSAL",
                        "Payment reversed: " + r.reversalReason(), r.amount(), r.id(), r.reversedAt()));
            }
        }

        // Same day: the loan and its charges first, then payments and reversals in the order they really happened.
        raw.sort(Comparator.comparing(Raw::date)
                .thenComparingInt(e -> e.rank() >= 2 ? 2 : e.rank())
                .thenComparing(Raw::at, Comparator.nullsFirst(Comparator.<Instant>naturalOrder()))
                .thenComparingLong(Raw::tie));
        List<RepaymentDtos.StatementEntry> entries = new ArrayList<>();
        BigDecimal balance = BigDecimal.ZERO;
        for (Raw e : raw) {
            balance = balance.add(e.signedAmount());
            entries.add(new RepaymentDtos.StatementEntry(e.date(), e.type(), e.description(),
                    e.signedAmount(), balance, e.repaymentId()));
        }
        return new RepaymentDtos.Statement(view, entries);
    }

    // ------------------------------------------------------------------ helpers

    private Plan plan(UUID tenantId, LoanRow loan, BigDecimal amount, LocalDate paymentDate) {
        LoanTerms terms = loan.terms();
        List<AssessedCharge> charges = ChargeAssessor.assess(terms, paymentDate);
        Map<BalanceComponent, BigDecimal> allocated =
                loans.allocatedNet(tenantId, List.of(loan.id())).getOrDefault(loan.id(), Map.of());
        Outstanding before = LedgerBalance.compute(terms, charges, allocated);
        try {
            List<Allocation> allocations = AllocationEngine.allocate(amount, before, terms.allocationOrder());
            return new Plan(charges, before, allocations, before.minus(allocations));
        } catch (OverpaymentException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "overpayment",
                    "The payment is more than the amount owed on that date", Map.of("outstanding", e.outstanding()));
        }
    }

    private void validateDate(UUID tenantId, LoanRow loan, LocalDate paymentDate) {
        if (paymentDate.isAfter(clock.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "payment_date_in_future", "The payment date cannot be in the future");
        }
        if (paymentDate.isBefore(loan.issueDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "payment_before_issue", "The payment date is before the loan was issued");
        }
        repayments.latestActivePaymentDate(tenantId, loan.id()).ifPresent(last -> {
            if (paymentDate.isBefore(last)) {
                throw new ApiException(HttpStatus.CONFLICT, "payment_out_of_order",
                        "Payments must be recorded in date order. The latest payment is dated " + last + ".");
            }
        });
    }

    private static void requireOpen(LoanRow loan) {
        if (!"ACTIVE".equals(loan.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "loan_not_active", "This loan is " + loan.status().toLowerCase() + " and cannot take payments");
        }
    }

    private static String requireKey(String key) {
        String trimmed = key == null ? "" : key.trim();
        if (trimmed.length() < MIN_KEY_LENGTH || trimmed.length() > MAX_KEY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "idempotency_key_required",
                    "Send a unique Idempotency-Key header of " + MIN_KEY_LENGTH + " to " + MAX_KEY_LENGTH + " characters");
        }
        return trimmed;
    }

    private RepaymentDtos.RepaymentView view(UUID tenantId, RepaymentRow row) {
        return views(tenantId, List.of(row)).get(0);
    }

    private List<RepaymentDtos.RepaymentView> views(UUID tenantId, List<RepaymentRow> rows) {
        Map<UUID, List<RepaymentDtos.AllocationLine>> allocations = allocationsByRepayment(tenantId, rows);
        return rows.stream().map(r -> new RepaymentDtos.RepaymentView(r.id(), r.loanId(), r.amount(),
                r.paymentDate(), r.recordedAt(), r.method(), r.reference(),
                allocations.getOrDefault(r.id(), List.of()), r.reversedAt() != null,
                r.reversedAt(), r.reversalReason())).toList();
    }

    private Map<UUID, List<RepaymentDtos.AllocationLine>> allocationsByRepayment(UUID tenantId, List<RepaymentRow> rows) {
        return repayments.originalAllocations(tenantId, rows.stream().map(RepaymentRow::id).toList()).stream()
                .collect(Collectors.groupingBy(RepaymentRepository.AllocationRow::repaymentId, LinkedHashMap::new,
                        Collectors.mapping(a -> new RepaymentDtos.AllocationLine(a.component().name(), a.amount()),
                                Collectors.toList())));
    }

    private static List<RepaymentDtos.AllocationLine> lines(List<Allocation> allocations) {
        return allocations.stream()
                .map(a -> new RepaymentDtos.AllocationLine(a.component().name(), a.amount())).toList();
    }

    private static LoanDtos.BalanceView balance(Outstanding o) {
        return new LoanDtos.BalanceView(o.principal(), o.interest(), o.penalty(), o.total());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static ApiException loanNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "loan_not_found", "Loan not found");
    }

    private static ApiException repaymentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "repayment_not_found", "Repayment not found");
    }
}