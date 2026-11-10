package com.lendLedger.backend.borrower;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class BorrowerDtos {

    private BorrowerDtos() { }

    public record CreateRequest(
            @NotBlank(message = "Name is required") @Size(max = 200, message = "Name is too long") String fullName,
            @NotBlank(message = "Phone is required") @Size(max = 30, message = "Phone is too long") String phone,
            @Email(message = "Enter a valid email address") @Size(max = 200, message = "Email is too long") String email,
            @NotBlank(message = "ID number is required") @Size(max = 40, message = "ID number is too long") String nationalId,
            @Size(max = 1000, message = "Note is too long") String note,
            @Size(max = 500, message = "Reason is too long") String duplicateReason) { }

    public record UpdateRequest(
            @NotBlank(message = "Name is required") @Size(max = 200, message = "Name is too long") String fullName,
            @NotBlank(message = "Phone is required") @Size(max = 30, message = "Phone is too long") String phone,
            @Email(message = "Enter a valid email address") @Size(max = 200, message = "Email is too long") String email,
            @NotBlank(message = "ID number is required") @Size(max = 40, message = "ID number is too long") String nationalId,
            @Size(max = 1000, message = "Note is too long") String note) { }

    public record Response(UUID id, String fullName, String phone, String email, String nationalId, String kycStatus,
                           String note, BorrowerStatus status, Instant createdAt, Instant updatedAt) {

        /** Full details, for the borrower's own page. */
        static Response from(Borrower b) {
            return new Response(b.getId(), b.getFullName(), b.getPhone(), b.getEmail(), b.getNationalId(),
                    b.getKycStatus(), b.getNote(), b.getStatus(), b.getCreatedAt(), b.getUpdatedAt());
        }

        /** For lists and duplicate warnings: the ID number is hidden except its last three characters. */
        static Response masked(Borrower b) {
            return new Response(b.getId(), b.getFullName(), b.getPhone(), b.getEmail(), mask(b.getNationalId()),
                    b.getKycStatus(), b.getNote(), b.getStatus(), b.getCreatedAt(), b.getUpdatedAt());
        }

        private static String mask(String id) {
            if (id == null) return null;
            return id.length() <= 3 ? "***" : "*".repeat(id.length() - 3) + id.substring(id.length() - 3);
        }
    }
}