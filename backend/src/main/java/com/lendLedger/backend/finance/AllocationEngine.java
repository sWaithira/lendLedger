package com.lendLedger.backend.finance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits one payment across components in the loan's allocation order (BR-05). Allocations are exact
 * subtractions from the outstanding amounts, so their sum always equals the payment: no residual cents.
 */
public final class AllocationEngine {

    private AllocationEngine() { }

    /** @throws OverpaymentException if the payment exceeds the total outstanding (BR-09) */
    public static List<Allocation> allocate(BigDecimal payment, Outstanding due, List<BalanceComponent> order) {
        Objects.requireNonNull(payment, "payment");
        if (payment.signum() <= 0 || payment.scale() > Money.SCALE)
            throw new IllegalArgumentException("payment must be positive with at most 2 decimal places");
        if (payment.compareTo(due.total()) > 0) throw new OverpaymentException(payment, due.total());

        List<Allocation> result = new ArrayList<>();
        BigDecimal remaining = payment;
        for (BalanceComponent component : order) {
            BigDecimal take = remaining.min(due.of(component));
            if (take.signum() > 0) {
                result.add(new Allocation(component, take));
                remaining = remaining.subtract(take);
            }
        }
        if (remaining.signum() != 0) throw new IllegalStateException("Unallocated remainder " + remaining);
        return List.copyOf(result);
    }
}