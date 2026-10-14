package com.lendLedger.backend.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A charge produced by {@link ChargeAssessor}. (type, kind, periodNumber) identifies it uniquely for a
 * loan, which is what lets persisted charge_event rows and freshly projected charges be merged safely.
 */
public record AssessedCharge(ChargeType type, PeriodKind kind, int periodNumber,
                             LocalDate effectiveDate, BigDecimal amount) {
    public BalanceComponent component() { return type.component(); }
}