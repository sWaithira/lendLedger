package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class MonthlyReportDtos {

    private MonthlyReportDtos() { }

    /** One calendar month in the lender's time zone. Reversals count in the month they were recorded. */
    public record MonthPoint(LocalDate monthStart, long loansIssued, BigDecimal principalLent,
                             BigDecimal collected, BigDecimal principalCollected,
                             BigDecimal interestCollected, BigDecimal penaltyCollected) { }
}