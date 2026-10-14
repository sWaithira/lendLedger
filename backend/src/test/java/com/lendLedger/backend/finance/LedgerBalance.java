package com.lendLedger.backend.finance;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Derives the balance from first principles (ADR-08): original principal plus assessed charges, minus
 * allocations NET of reversals. Cached totals, if any are ever stored, must equal this.
 */
public final class LedgerBalance {

    private LedgerBalance() { }

    public static Outstanding compute(LoanTerms terms,
                                      List<AssessedCharge> charges,
                                      Map<BalanceComponent, BigDecimal> allocatedNet) {
        Map<BalanceComponent, BigDecimal> owed = new EnumMap<>(BalanceComponent.class);
        owed.put(BalanceComponent.PRINCIPAL, terms.principal());
        owed.put(BalanceComponent.INTEREST, Money.zero());
        owed.put(BalanceComponent.PENALTY, Money.zero());
        for (AssessedCharge c : charges) owed.merge(c.component(), c.amount(), BigDecimal::add);

        return new Outstanding(
                remaining(owed, allocatedNet, BalanceComponent.PENALTY),
                remaining(owed, allocatedNet, BalanceComponent.INTEREST),
                remaining(owed, allocatedNet, BalanceComponent.PRINCIPAL));
    }

    private static BigDecimal remaining(Map<BalanceComponent, BigDecimal> owed,
                                        Map<BalanceComponent, BigDecimal> allocated,
                                        BalanceComponent c) {
        BigDecimal r = owed.get(c).subtract(allocated.getOrDefault(c, BigDecimal.ZERO));
        if (r.signum() < 0) throw new IllegalStateException("Over-allocated " + c + ": " + r);
        return r;
    }
}