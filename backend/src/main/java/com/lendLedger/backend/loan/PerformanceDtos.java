package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PerformanceDtos {

    private PerformanceDtos() { }

    /** What happened to the loans issued in the period. The four numbers add up to loansIssued. */
    public record Outcomes(long paidOff, long rolledOver, long onTrack, long overdue) { }

    /** Money received through one payment method in the period, net of reversals. */
    public record MethodTotal(String method, BigDecimal total) { }

    public record Performance(LocalDate from, LocalDate to, long loansIssued, BigDecimal principalLent,
                              BigDecimal received, BigDecimal interestEarned, Outcomes outcomes,
                              List<MethodTotal> byMethod) { }
}