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
 * <p>LEGACY_FLAT (old loans, unchanged): every term period is charged at origination; each completed
 * overdue month adds one INTEREST and, if configured, one PENALTY.
 *
 * <p>FIXED_FEE (Engine v2): interest is charged ONCE at origination. Penalty 1 lands the day after
 * due date + grace; penalty k (k >= 2) lands overdueStart + (k-1) months. Never compounds.
 *
 * <p>Accrual stops when the loan is settled: callers pass {@code min(asOf, settledOn)}. Because payments
 * clear PENALTY first, principal and interest can only be fully paid once all penalties are paid, so a
 * settled loan never gains further penalties.
 */
public final class ChargeAssessor {

    private static final int MAX_OVERDUE_PERIODS = 600;

    private ChargeAssessor() { }

    public static List<AssessedCharge> assess(LoanTerms terms, LocalDate asOf) {
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(asOf, "asOf");
        if (asOf.isBefore(terms.issueDate())) return List.of();
        return terms.model() == InterestModel.FIXED_FEE ? assessFixedFee(terms, asOf) : assessLegacy(terms, asOf);
    }

    private static List<AssessedCharge> assessFixedFee(LoanTerms terms, LocalDate asOf) {
        List<AssessedCharge> out = new ArrayList<>();
        BigDecimal interest = terms.periodInterest();
        if (interest.signum() > 0) {
            out.add(new AssessedCharge(ChargeType.INTEREST, PeriodKind.TERM, 1, terms.issueDate(), interest));
        }
        BigDecimal penaltyPerPeriod = terms.periodPenalty();
        BigDecimal penaltyAssessed = Money.zero();
        int n = completedOverduePeriods(terms, asOf);
        for (int k = 1; k <= n; k++) {
            BigDecimal penalty = penaltyPerPeriod;
            if (terms.penaltyCap() != null) {
                penalty = penalty.min(terms.penaltyCap().subtract(penaltyAssessed));
            }
            if (penalty.signum() > 0) {
                out.add(new AssessedCharge(ChargeType.PENALTY, PeriodKind.OVERDUE, k, penaltyDate(terms, k), penalty));
                penaltyAssessed = penaltyAssessed.add(penalty);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static List<AssessedCharge> assessLegacy(LoanTerms terms, LocalDate asOf) {
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

    /** FIXED_FEE: the date penalty k takes effect (k >= 1). */
    public static LocalDate penaltyDate(LoanTerms terms, int k) {
        return k == 1 ? terms.overdueStart().plusDays(1) : terms.overdueStart().plusMonths(k - 1L);
    }

    /**
     * LEGACY_FLAT: whole months completed since the overdue clock started.
     * FIXED_FEE: number of penalties in force on asOf (the "penalty month" shown to the lender).
     * Both are 0 while the loan is not overdue.
     */
    public static int completedOverduePeriods(LoanTerms terms, LocalDate asOf) {
        int k = 0;
        if (terms.model() == InterestModel.FIXED_FEE) {
            while (k < MAX_OVERDUE_PERIODS && !penaltyDate(terms, k + 1).isAfter(asOf)) k++;
            return k;
        }
        LocalDate anchor = terms.overdueStart();
        while (k < MAX_OVERDUE_PERIODS && !anchor.plusMonths(k + 1L).isAfter(asOf)) k++;
        return k;
    }

    /** True when the as-of date is strictly after due date + grace (derived status, ADR-02). */
    public static boolean isOverdue(LoanTerms terms, LocalDate asOf) {
        return asOf.isAfter(terms.overdueStart());
    }
}