package com.lendLedger.backend.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * The contractual terms of one loan, copied from the rule version at origination (the "terms snapshot").
 * Everything the engine needs is in here, so a calculation can be reproduced years later without
 * consulting any mutable configuration.
 *
 * @param interestRatePerPeriod   e.g. 0.30 for 30% per month, applied to ORIGINAL principal (FLAT)
 * @param penaltyRatePerPeriod    non-compounding, per overdue period, on original principal; 0 = none
 * @param penaltyCap              maximum total penalty over the life of the loan; null = no cap
 */
public record LoanTerms(BigDecimal principal,
                        LocalDate issueDate,
                        int termPeriods,
                        BigDecimal interestRatePerPeriod,
                        int graceDays,
                        BigDecimal penaltyRatePerPeriod,
                        BigDecimal penaltyCap,
                        List<BalanceComponent> allocationOrder,
                        RoundingMode roundingMode) {

    public static final List<BalanceComponent> DEFAULT_ORDER =
            List.of(BalanceComponent.PENALTY, BalanceComponent.INTEREST, BalanceComponent.PRINCIPAL);

    public LoanTerms {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(interestRatePerPeriod, "interestRatePerPeriod");
        Objects.requireNonNull(penaltyRatePerPeriod, "penaltyRatePerPeriod");
        Objects.requireNonNull(allocationOrder, "allocationOrder");
        Objects.requireNonNull(roundingMode, "roundingMode");
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

    /** FLAT loan with the demo defaults: no grace, no penalty, penalty -> interest -> principal. */
    public static LoanTerms flat(BigDecimal principal, LocalDate issueDate, int termPeriods, BigDecimal ratePerPeriod) {
        return new LoanTerms(principal, issueDate, termPeriods, ratePerPeriod, 0,
                BigDecimal.ZERO, null, DEFAULT_ORDER, RoundingMode.HALF_UP);
    }

    public LoanTerms withGraceDays(int days) {
        return new LoanTerms(principal, issueDate, termPeriods, interestRatePerPeriod, days,
                penaltyRatePerPeriod, penaltyCap, allocationOrder, roundingMode);
    }

    public LoanTerms withPenalty(BigDecimal ratePerPeriod, BigDecimal cap) {
        return new LoanTerms(principal, issueDate, termPeriods, interestRatePerPeriod, graceDays,
                ratePerPeriod, cap, allocationOrder, roundingMode);
    }

    public LoanTerms withAllocationOrder(List<BalanceComponent> order) {
        return new LoanTerms(principal, issueDate, termPeriods, interestRatePerPeriod, graceDays,
                penaltyRatePerPeriod, penaltyCap, order, roundingMode);
    }

    /** Due date = issue date + term, using java.time month arithmetic (clamps to month end). */
    public LocalDate dueDate() { return issueDate.plusMonths(termPeriods); }

    /** The loan counts as overdue after this date (due date plus grace days). */
    public LocalDate overdueStart() { return dueDate().plusDays(graceDays); }

    public BigDecimal periodInterest() { return Money.round(principal.multiply(interestRatePerPeriod), roundingMode); }

    public BigDecimal periodPenalty() { return Money.round(principal.multiply(penaltyRatePerPeriod), roundingMode); }
}