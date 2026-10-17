package com.lendLedger.backend.borrower;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response shapes for the borrower API. The entity itself is never sent to the client. */
public final class BorrowerDtos {

    private BorrowerDtos() { }

    public record CreateRequest(
            @NotBlank(message = "Name is required") @Size(max = 200, message = "Name is too long") String fullName,
            @NotBlank(message = "Phone is required") @Size(max = 30, message = "Phone is too long") String phone,
            @Size(max = 1000, message = "Note is too long") String note,
            @Size(max = 500, message = "Reason is too long") String duplicateReason) { }

    public record UpdateRequest(
            @NotBlank(message = "Name is required") @Size(max = 200, message = "Name is too long") String fullName,
            @NotBlank(message = "Phone is required") @Size(max = 30, message = "Phone is too long") String phone,
            @Size(max = 1000, message = "Note is too long") String note) { }

    public record Response(UUID id, String fullName, String phone, String note,
                           BorrowerStatus status, Instant createdAt, Instant updatedAt) {

        static Response from(Borrower b) {
            return new Response(b.getId(), b.getFullName(), b.getPhone(), b.getNote(),
                    b.getStatus(), b.getCreatedAt(), b.getUpdatedAt());
        }
    }
}