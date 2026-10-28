package com.lendLedger.backend.loan;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class RolloverDtos {

    private RolloverDtos() { }

    /** Both fields are optional: the date defaults to today and the term to the old loan's term. */
    public record Request(LocalDate rolloverDate,
                          @Min(value = 1, message = "Term must be at least 1 month")
                          @Max(value = 60, message = "Term cannot exceed 60 months") Integer termPeriods) { }

    public record LinkView(UUID id, int loanNumber) { }

    public record Links(LinkView rolledFrom, LinkView rolledInto) { }
}