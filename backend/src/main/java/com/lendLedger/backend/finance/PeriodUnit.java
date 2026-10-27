package com.lendLedger.backend.finance;

import java.time.LocalDate;

/** The unit a loan term is counted in. */
public enum PeriodUnit {
    DAY, WEEK, MONTH;

    /** Adds n units to a date (month arithmetic clamps to month end). */
    public LocalDate addTo(LocalDate date, long n) {
        return switch (this) {
            case DAY -> date.plusDays(n);
            case WEEK -> date.plusWeeks(n);
            case MONTH -> date.plusMonths(n);
        };
    }
}