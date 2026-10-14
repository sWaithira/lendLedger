package com.lendLedger.backend.finance;

import java.math.BigDecimal;
import java.util.List;

/** What is still owed, split by component. All amounts are non-negative with scale 2. */
public record Outstanding(BigDecimal penalty, BigDecimal interest, BigDecimal principal) {

    public Outstanding {
        if (penalty.signum() < 0 || interest.signum() < 0 || principal.signum() < 0)
            throw new IllegalStateException("Outstanding component is negative: " + penalty + "/" + interest + "/" + principal);
    }

    public BigDecimal of(BalanceComponent component) {
        return switch (component) {
            case PENALTY -> penalty;
            case INTEREST -> interest;
            case PRINCIPAL -> principal;
        };
    }

    public BigDecimal total() { return penalty.add(interest).add(principal); }

    public boolean isSettled() { return total().signum() == 0; }

    /** The balance that would remain after applying these allocations. */
    public Outstanding minus(List<Allocation> allocations) {
        BigDecimal p = penalty, i = interest, pr = principal;
        for (Allocation a : allocations) {
            switch (a.component()) {
                case PENALTY -> p = p.subtract(a.amount());
                case INTEREST -> i = i.subtract(a.amount());
                case PRINCIPAL -> pr = pr.subtract(a.amount());
            }
        }
        return new Outstanding(p, i, pr);
    }
}