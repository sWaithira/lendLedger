package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lendLedger.backend.audit.AuditJson;
import com.lendLedger.backend.audit.AuditService;
import com.lendLedger.backend.common.ApiException;
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

/**
 * Rolls the remaining principal of a loan into a new loan once its interest and penalties are fully paid.
 * The old loan is closed by a non-cash ROLLOVER ledger row; nothing is edited or deleted.
 */
@Service
public class RolloverService {

    private final LoanRepository loans;
    private final RepaymentRepository repayments;
    private final RolloverRepository links;
    private final LoanService loanService;
    private final AuditService audit;
    private final TenantClock clock;

    public RolloverService(LoanRepository loans, RepaymentRepository repayments, RolloverRepository links,
                           LoanService loanService, AuditService audit, TenantClock clock) {
        this.loans = loans;
        this.repayments = repayments;
        this.links = links;
        this.loanService = loanService;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public LoanDtos.LoanView rollover(UUID loanId, RolloverDtos.Request request) {
        LendLedgerPrincipal user = CurrentUser.require();
        UUID tenantId = user.tenantId();

        // Lock first, like a payment, so a rollover and a payment on one loan never interleave.
        LoanRow loan = loans.lockById(tenantId, loanId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "loan_not_found", "Loan not found"));
        if (!"ACTIVE".equals(loan.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "loan_not_active",
                    "This loan is " + loan.status().toLowerCase() + " and cannot be rolled over");
        }

        LocalDate date = request.rolloverDate() == null ? clock.today() : request.rolloverDate();
        if (date.isAfter(clock.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "rollover_date_in_future", "The rollover date cannot be in the future");
        }
        if (date.isBefore(loan.issueDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "rollover_before_issue", "The rollover date is before the loan was issued");
        }
        repayments.latestActivePaymentDate(tenantId, loanId).ifPresent(last -> {
            if (date.isBefore(last)) {
                throw new ApiException(HttpStatus.CONFLICT, "rollover_out_of_order",
                        "The rollover cannot be dated before the latest payment (" + last + ")");
            }
        });

        LoanTerms terms = loan.terms();
        List<AssessedCharge> charges = ChargeAssessor.assess(terms, date);
        Map<BalanceComponent, BigDecimal> allocated =
                loans.allocatedNet(tenantId, List.of(loanId)).getOrDefault(loanId, Map.of());
        Outstanding owed = LedgerBalance.compute(terms, charges, allocated);

        if (owed.interest().signum() > 0 || owed.penalty().signum() > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "charges_unpaid",
                    "Interest and penalties must be paid in full on that date before the principal can be rolled over",
                    Map.of("interest", owed.interest(), "penalty", owed.penalty()));
        }
        BigDecimal principal = owed.principal();
        if (principal.signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "nothing_to_roll", "There is no principal left to roll over");
        }

        int term = request.termPeriods() == null ? loan.termPeriods() : request.termPeriods();
        LoanService.Quote quote = loanService.quote(loan.productId(), principal, date, term);
        LoanTerms newTerms = quote.terms();
        LoanRepository.RuleInfo rule = quote.rule();

        // 1. Bring the old loan's charges up to the rollover date (same as a payment does).
        for (AssessedCharge c : charges) {
            loans.insertCharge(tenantId, loanId, c.type().name(), c.kind().name(), c.periodNumber(),
                    c.effectiveDate(), c.amount(), loan.ruleVersionId());
        }

        // 2. Create the new loan with the next number and today's rules.
        UUID newLoanId = UUID.randomUUID();
        int number = loans.nextLoanNumber(tenantId);
        loans.insertLoan(newLoanId, number, tenantId, loan.borrowerId(), loan.productId(), rule.ruleVersionId(),
                principal, rule.currency(), date, term, newTerms.dueDate(),
                AuditJson.toJson(LoanService.snapshot(rule, newTerms)), user.userId());
        for (AssessedCharge c : ChargeAssessor.assess(newTerms, date)) {
            loans.insertCharge(tenantId, newLoanId, c.type().name(), c.kind().name(), c.periodNumber(),
                    c.effectiveDate(), c.amount(), rule.ruleVersionId());
        }
        links.link(tenantId, newLoanId, loanId);

        // 3. Close the old loan: a non-cash ROLLOVER row carries the principal, then the loan settles.
        UUID repaymentId = UUID.randomUUID();
        String reference = "Rolled into " + LoanDtos.reference(number);
        String hash = RequestHash.of(loanId.toString(), principal.toPlainString(), date.toString(), "ROLLOVER", reference);
        repayments.insertRepayment(repaymentId, tenantId, loanId, principal, loan.currency(), date,
                "ROLLOVER", reference, "rollover-" + repaymentId, hash, user.userId());
        repayments.insertAllocation(tenantId, repaymentId, loanId, BalanceComponent.PRINCIPAL.name(), principal, 1);
        loans.markSettled(tenantId, loanId, date);

        audit.record("LOAN_ROLLED_OVER", "LOAN", loanId, null, Map.of(
                "oldLoan", LoanDtos.reference(loan.loanNumber()),
                "newLoan", LoanDtos.reference(number),
                "principal", principal.toPlainString()));

        return loanService.get(newLoanId);
    }

    @Transactional(readOnly = true)
    public RolloverDtos.Links linksFor(UUID loanId) {
        UUID tenantId = CurrentUser.tenantId();
        loanService.get(loanId);   // 404 if missing or another lender's
        return new RolloverDtos.Links(links.rolledFrom(tenantId, loanId).orElse(null),
                links.rolledInto(tenantId, loanId).orElse(null));
    }
}