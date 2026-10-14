package com.lendLedger.backend.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic charge assessment (ADR-01): the same terms and as-of date always give the same charges.
 *
 * <p>FLAT method, demo defaults (spec 13.3, BR-02/03/04/06):
 * <ul>
 *   <li>Every term period is charged at origination.</li>
 *   <li>After the due date (plus grace), each completed further month adds one INTEREST period and, if
 *       configured, one PENALTY period. Month arithmetic is always from the anchor
 *       ({@code overdueStart.plusMonths(k)}), never iterative, so month-end dates cannot drift.</li>
 *   <li>Charges are computed on the ORIGINAL principal and never compound.</li>
 * </ul>
 *
 * <p>Accrual stops when the loan is settled: callers pass {@code min(asOf, settledOn)}.
 */
public final class ChargeAssessor {

    private static final int MAX_OVERDUE_PERIODS = 600;   // safety stop (50 years of months)

    private ChargeAssessor() { }

    public static List<AssessedCharge> assess(LoanTerms terms, LocalDate asOf) {
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(asOf, "asOf");
        if (asOf.isBefore(terms.issueDate())) return List.of();

        List<AssessedCharge> out = new ArrayList<>();
        BigDecimal interest = terms.periodInterest();

        if (interest.signum() > 0) {
            for (int p = 1; p <= terms.termPeriods(); p++) {
                out.add(new AssessedCharge(ChargeType.INTEREST, PeriodKind.TERM, p, terms.issueDate(), interest));
            }
        }

        int overdue = completedOverduePeriods(terms, asOf);
        BigDecimal penaltyPerPeriod = terms.periodPenalty();
        BigDecimal penaltyAssessed = Money.zero();

        for (int k = 1; k <= overdue; k++) {
            LocalDate effective = terms.overdueStart().plusMonths(k);
            if (interest.signum() > 0) {
                out.add(new AssessedCharge(ChargeType.INTEREST, PeriodKind.OVERDUE, k, effective, interest));
            }
            BigDecimal penalty = penaltyPerPeriod;
            if (terms.penaltyCap() != null) {
                penalty = penalty.min(terms.penaltyCap().subtract(penaltyAssessed));
            }
            if (penalty.signum() > 0) {
                out.add(new AssessedCharge(ChargeType.PENALTY, PeriodKind.OVERDUE, k, effective, penalty));
                penaltyAssessed = penaltyAssessed.add(penalty);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Whole months completed since the overdue clock started; 0 while the loan is not yet overdue. */
    public static int completedOverduePeriods(LoanTerms terms, LocalDate asOf) {
        LocalDate anchor = terms.overdueStart();
        int k = 0;
        while (k < MAX_OVERDUE_PERIODS && !anchor.plusMonths(k + 1L).isAfter(asOf)) k++;
        return k;
    }

    /** True when the as-of date is strictly after due date + grace (derived status, ADR-02). */
    public static boolean isOverdue(LoanTerms terms, LocalDate asOf) {
        return asOf.isAfter(terms.overdueStart());
    }
}