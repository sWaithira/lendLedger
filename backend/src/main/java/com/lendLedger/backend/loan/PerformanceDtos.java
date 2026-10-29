package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PerformanceDtos {

    private PerformanceDtos() { }

    /** What happened to the loans issued in the period. The four numbers add up to loansIssued. */
    public record Outcomes(long paidOff, long rolledOver, long onTrack, long overdue) { }

    public record Performance(LocalDate from, LocalDate to, long loansIssued, BigDecimal principalLent,
                              BigDecimal received, BigDecimal interestEarned, Outcomes outcomes) { }
}