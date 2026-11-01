package com.lendLedger.backend.borrower;

import java.time.Instant;
import java.time.LocalDate;

public final class BorrowerIdDtos {

    private BorrowerIdDtos() { }

    /** status: NONE, ON_FILE or VERIFIED. Never carries image data. */
    public record IdStatus(String status, boolean front, boolean back, Instant verifiedAt, String verifiedByName,
                           LocalDate consentOn) { }

    public record ConsentRequest(LocalDate consentOn) { }
}