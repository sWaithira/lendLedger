package com.lendLedger.backend.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * The contractual terms of one loan, copied from the rule version at origination (the "terms snapshot").
 *
 * @param interestRatePerPeriod e.g. 0.30 for 30%. LEGACY_FLAT: per term month. FIXED_FEE: once per loan.
 * @param penaltyRatePerPeriod  LEGACY_FLAT: share of principal per overdue month. FIXED_FEE: share of the
 *                              interest amount per overdue month (1.00 = 100%).
 * @param penaltyCap            maximum total penalty over the life of the loan; null = no cap
 */
public record LoanTerms(BigDecimal principal,
                        LocalDate issueDate,
                        int termPeriods,
                        BigDecimal interestRatePerPeriod,
                        int graceDays,
                        BigDecimal penaltyRatePerPeriod,
                        BigDecimal penaltyCap,
                        List<BalanceComponent> allocationOrder,
                        RoundingMode roundingMode,
                        PeriodUnit periodUnit,
                        InterestModel model) {

    public static final List<BalanceComponent> DEFAULT_ORDER =
            List.of(BalanceComponent.PENALTY, BalanceComponent.INTEREST, BalanceComponent.PRINCIPAL);

    public LoanTerms {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(interestRatePerPeriod, "interestRatePerPeriod");
        Objects.requireNonNull(penaltyRatePerPeriod, "penaltyRatePerPeriod");
        Objects.requireNonNull(allocationOrder, "allocationOrder");
        Objects.requireNonNull(roundingMode, "roundingMode");
        Objects.requireNonNull(periodUnit, "periodUnit");
        Objects.requireNonNull(model, "model");
        if (principal.signum() <= 0 || principal.scale() > Money.SCALE)
            throw new IllegalArgumentException("principal must be positive with at most 2 decimal places");
        if (termPeriods < 1) throw new IllegalArgumentException("termPeriods must be at least 1");
        if (interestRatePerPeriod.signum() < 0 || penaltyRatePerPeriod.signum() < 0)
            throw new IllegalArgumentException("rates must not be negative");
        if (graceDays < 0) throw new IllegalArgumentException("graceDays must not be negative");
        if (penaltyCap != null && penaltyCap.signum() < 0)
            throw new IllegalArgumentException("penaltyCap must not be negative");
        if (allocationOrder.size() != 3
                || !EnumSet.copyOf(allocationOrder).equals(EnumSet.allOf(BalanceComponent.class)))
            throw new IllegalArgumentException("allocationOrder must contain each component exactly once");
        allocationOrder = List.copyOf(allocationOrder);
    }

    /** Original 9-argument form: monthly terms, LEGACY_FLAT. Keeps existing callers and old loans working. */
    public LoanTerms(BigDecimal principal, LocalDate issueDate, int termPeriods, BigDecimal interestRatePerPeriod,
                     int graceDays, BigDecimal penaltyRatePerPeriod, BigDecimal penaltyCap,
                     List<BalanceComponent> allocationOrder, RoundingMode roundingMode) {
        this(principal, issueDate, termPeriods, interestRatePerPeriod, graceDays, penaltyRatePerPeriod,
                penaltyCap, allocationOrder, roundingMode, PeriodUnit.MONTH, InterestModel.LEGACY_FLAT);
    }

    /** LEGACY_FLAT loan with the old demo defaults: no grace, no penalty. */
    public static LoanTerms flat(BigDecimal principal, LocalDate issueDate, int termPeriods, BigDecimal ratePerPeriod) {
        return new LoanTerms(principal, issueDate, termPeriods, ratePerPeriod, 0,
                BigDecimal.ZERO, null, DEFAULT_ORDER, RoundingMode.HALF_UP);
    }

    /** FIXED_FEE loan: interest once, penalty = 100% of interest per overdue month, no grace, no cap. */
    public static LoanTerms fixedFee(BigDecimal principal, LocalDate issueDate, int termPeriods,
                                     PeriodUnit unit, BigDecimal interestRate) {
        return new LoanTerms(principal, issueDate, termPeriods, interestRate, 0, BigDecimal.ONE, null,
                DEFAULT_ORDER, RoundingMode.HALF_UP, unit, InterestModel.FIXED_FEE);
    }

    public LoanTerms withGraceDays(int days) {
        return new LoanTerms(principal, issueDate, termPeriods, interestRatePerPeriod, days,
                penaltyRatePerPeriod, penaltyCap, allocationOrder, roundingMode, periodUnit, model);
    }

    public LoanTerms withPenalty(BigDecimal ratePerPeriod, BigDecimal cap) {
        return new LoanTerms(principal, issueDate, termPeriods, interestRatePerPeriod, graceDays,
                ratePerPeriod, cap, allocationOrder, roundingMode, periodUnit, model);
    }

    public LoanTerms withAllocationOrder(List<BalanceComponent> order) {
        return new LoanTerms(principal, issueDate, termPeriods, interestRatePerPeriod, graceDays,
                penaltyRatePerPeriod, penaltyCap, order, roundingMode, periodUnit, model);
    }

    /** Due date = issue date + term in the loan's unit (month arithmetic clamps to month end). */
    public LocalDate dueDate() { return periodUnit.addTo(issueDate, termPeriods); }

    /** The loan counts as overdue after this date (due date plus grace days). */
    public LocalDate overdueStart() { return dueDate().plusDays(graceDays); }

    /** FIXED_FEE: the one-off interest in whole shillings. LEGACY_FLAT: interest per term period, 2 dp. */
    public BigDecimal periodInterest() {
        BigDecimal raw = principal.multiply(interestRatePerPeriod);
        return model == InterestModel.FIXED_FEE ? wholeShillings(raw) : Money.round(raw, roundingMode);
    }

    /** FIXED_FEE: penalty = interest x rate, whole shillings. LEGACY_FLAT: share of principal, 2 dp. */
    public BigDecimal periodPenalty() {
        if (model == InterestModel.FIXED_FEE) {
            return wholeShillings(periodInterest().multiply(penaltyRatePerPeriod));
        }
        return Money.round(principal.multiply(penaltyRatePerPeriod), roundingMode);
    }

    private BigDecimal wholeShillings(BigDecimal value) {
        return value.setScale(0, roundingMode).setScale(Money.SCALE, RoundingMode.UNNECESSARY);
    }
}