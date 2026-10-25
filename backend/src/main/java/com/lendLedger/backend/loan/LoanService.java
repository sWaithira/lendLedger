package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lendLedger.backend.audit.AuditJson;
import com.lendLedger.backend.audit.AuditService;
import com.lendLedger.backend.common.ApiException;
import com.lendLedger.backend.common.PageResponse;
import com.lendLedger.backend.common.TenantClock;
import com.lendLedger.backend.finance.AssessedCharge;
import com.lendLedger.backend.finance.BalanceComponent;
import com.lendLedger.backend.finance.ChargeAssessor;
import com.lendLedger.backend.finance.LedgerBalance;
import com.lendLedger.backend.finance.LoanTerms;
import com.lendLedger.backend.finance.Outstanding;
import com.lendLedger.backend.security.CurrentUser;
import com.lendLedger.backend.security.LendLedgerPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DUE_SOON_DAYS = 7;

    private record Quote(LoanRepository.RuleInfo rule, LoanTerms terms) { }

    private final LoanRepository loans;
    private final AuditService audit;
    private final TenantClock clock;

    public LoanService(LoanRepository loans, AuditService audit, TenantClock clock) {
        this.loans = loans;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ preview and create

    @Transactional(readOnly = true)
    public LoanDtos.Preview preview(LoanDtos.PreviewRequest request) {
        Quote quote = quote(request.productId(), request.principal(), request.issueDate(), request.termPeriods());
        List<AssessedCharge> charges = ChargeAssessor.assess(quote.terms(), request.issueDate());
        BigDecimal interest = sum(charges);
        return new LoanDtos.Preview(quote.rule().ruleVersionId(), quote.rule().versionNumber(),
                quote.rule().ratePerPeriod(), request.principal(), request.issueDate(), quote.terms().dueDate(),
                request.termPeriods(), charges.stream().map(LoanService::line).toList(),
                interest, request.principal().add(interest));
    }

    @Transactional
    public LoanDtos.LoanView create(LoanDtos.CreateRequest request) {
        LendLedgerPrincipal user = CurrentUser.require();
        String borrowerStatus = loans.borrowerStatus(user.tenantId(), request.borrowerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "borrower_not_found", "Borrower not found"));
        if (!"ACTIVE".equals(borrowerStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "borrower_archived", "This borrower is archived");
        }

        Quote quote = quote(request.productId(), request.principal(), request.issueDate(), request.termPeriods());
        LoanTerms terms = quote.terms();
        LoanRepository.RuleInfo rule = quote.rule();

        UUID loanId = UUID.randomUUID();
        int loanNumber = loans.nextLoanNumber(user.tenantId());
        loans.insertLoan(loanId, loanNumber, user.tenantId(), request.borrowerId(), request.productId(),
                rule.ruleVersionId(), request.principal(), rule.currency(), request.issueDate(),
                request.termPeriods(), terms.dueDate(), AuditJson.toJson(snapshot(rule, terms)), user.userId());

        for (AssessedCharge c : ChargeAssessor.assess(terms, request.issueDate())) {
            loans.insertCharge(user.tenantId(), loanId, c.type().name(), c.kind().name(), c.periodNumber(),
                    c.effectiveDate(), c.amount(), rule.ruleVersionId());
        }

        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("loan", LoanDtos.reference(loanNumber));
        detail.put("principal", request.principal().toPlainString());
        detail.put("termPeriods", String.valueOf(request.termPeriods()));
        detail.put("ruleVersion", String.valueOf(rule.versionNumber()));
        audit.record("LOAN_CREATED", "LOAN", loanId, null, detail);

        return get(loanId);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public LoanDtos.LoanView get(UUID id) {
        UUID tenantId = CurrentUser.tenantId();
        LoanRow row = loans.findById(tenantId, id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "loan_not_found", "Loan not found"));
        Map<BalanceComponent, BigDecimal> allocated =
                loans.allocatedNet(tenantId, List.of(id)).getOrDefault(id, Map.of());
        return toView(row, allocated, clock.today());
    }

    @Transactional(readOnly = true)
    public PageResponse<LoanDtos.LoanView> list(UUID borrowerId, String status, int page, int size) {
        UUID tenantId = CurrentUser.tenantId();
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 0);

        long total = loans.count(tenantId, borrowerId, status);
        List<LoanRow> rows = loans.list(tenantId, borrowerId, status, pageSize, pageNumber * pageSize);
        Map<UUID, Map<BalanceComponent, BigDecimal>> allocated =
                loans.allocatedNet(tenantId, rows.stream().map(LoanRow::id).toList());

        LocalDate today = clock.today();
        List<LoanDtos.LoanView> items = new ArrayList<>();
        for (LoanRow row : rows) {
            items.add(toView(row, allocated.getOrDefault(row.id(), Map.of()), today));
        }
        int totalPages = (int) Math.ceil(total / (double) pageSize);
        return new PageResponse<>(items, pageNumber, pageSize, total, totalPages);
    }

    @Transactional(readOnly = true)
    public List<LoanDtos.ProductView> products() {
        return loans.listProducts(CurrentUser.tenantId(), clock.today());
    }

    // ------------------------------------------------------------------ helpers

    private Quote quote(UUID productId, BigDecimal principal, LocalDate issueDate, int termPeriods) {
        UUID tenantId = CurrentUser.tenantId();
        if (issueDate.isAfter(clock.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "issue_date_in_future", "The issue date cannot be in the future");
        }
        LoanRepository.RuleInfo rule = loans.findEffectiveRule(tenantId, productId, issueDate)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "no_effective_rule_version",
                        "No approved rules apply to this product on that date"));
        if (!"FLAT".equals(rule.interestMethod())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "unsupported_interest_method",
                    "Interest method " + rule.interestMethod() + " is not supported yet");
        }
        if (principal.compareTo(rule.minPrincipal()) < 0 || principal.compareTo(rule.maxPrincipal()) > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "principal_out_of_range",
                    "Amount must be between " + rule.minPrincipal() + " and " + rule.maxPrincipal(),
                    Map.of("min", rule.minPrincipal(), "max", rule.maxPrincipal()));
        }
        if (termPeriods > rule.maxTermPeriods()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "term_too_long",
                    "This product allows loans of up to " + rule.maxTermPeriods() + " month"
                            + (rule.maxTermPeriods() == 1 ? "" : "s"));
        }
        try {
            LoanTerms terms = new LoanTerms(principal, issueDate, termPeriods, rule.ratePerPeriod(), rule.graceDays(),
                    rule.penaltyRatePerPeriod(), rule.penaltyCap(), rule.allocationOrder(), rule.roundingMode());
            return new Quote(rule, terms);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_terms", e.getMessage());
        }
    }

    LoanDtos.LoanView toView(LoanRow row, Map<BalanceComponent, BigDecimal> allocated, LocalDate today) {
        LoanTerms terms = row.terms();
        LocalDate asOf = (row.settledOn() != null && row.settledOn().isBefore(today)) ? row.settledOn() : today;
        Outstanding owed = LedgerBalance.compute(terms, ChargeAssessor.assess(terms, asOf), allocated);

        boolean open = "ACTIVE".equals(row.status());
        boolean overdue = open && !owed.isSettled() && ChargeAssessor.isOverdue(terms, today);
        // Days are counted from the end of the lender's grace period: the day the loan really became overdue.
        long daysOverdue = overdue ? ChronoUnit.DAYS.between(terms.overdueStart(), today) : 0;
        String display;
        if (!open) display = row.status();
        else if (overdue) display = "OVERDUE";
        else if (today.isAfter(row.dueDate())) display = "GRACE";
        else if (!today.isBefore(row.dueDate().minusDays(DUE_SOON_DAYS))) display = "DUE_SOON";
        else display = "CURRENT";

        return new LoanDtos.LoanView(row.id(), row.loanNumber(), row.borrowerId(), row.borrowerName(), row.productId(),
                row.productName(), row.principal(), row.currency(), row.issueDate(), row.termPeriods(),
                row.dueDate(), row.ratePerPeriod(), row.ruleVersionNumber(), row.status(), display, daysOverdue,
                asOf, new LoanDtos.BalanceView(owed.principal(), owed.interest(), owed.penalty(), owed.total()));
    }

    private static Map<String, String> snapshot(LoanRepository.RuleInfo rule, LoanTerms terms) {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("ruleVersionId", rule.ruleVersionId().toString());
        s.put("ruleVersionNumber", String.valueOf(rule.versionNumber()));
        s.put("interestMethod", rule.interestMethod());
        s.put("principal", terms.principal().toPlainString());
        s.put("issueDate", terms.issueDate().toString());
        s.put("termPeriods", String.valueOf(terms.termPeriods()));
        s.put("dueDate", terms.dueDate().toString());
        s.put("ratePerPeriod", terms.interestRatePerPeriod().toPlainString());
        s.put("graceDays", String.valueOf(terms.graceDays()));
        s.put("penaltyRatePerPeriod", terms.penaltyRatePerPeriod().toPlainString());
        s.put("penaltyCap", terms.penaltyCap() == null ? null : terms.penaltyCap().toPlainString());
        s.put("allocationOrder", terms.allocationOrder().toString());
        s.put("roundingMode", terms.roundingMode().name());
        return s;
    }

    private static LoanDtos.ChargeLine line(AssessedCharge c) {
        return new LoanDtos.ChargeLine(c.type().name(), c.kind().name(), c.periodNumber(), c.effectiveDate(), c.amount());
    }

    private static BigDecimal sum(List<AssessedCharge> charges) {
        return charges.stream().map(AssessedCharge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}