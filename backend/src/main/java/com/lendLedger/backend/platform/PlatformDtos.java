package com.lendLedger.backend.platform;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PlatformDtos {

    private PlatformDtos() { }

    /** Lender metadata and counts only. Never borrower, loan or payment details. */
    public record LenderRow(UUID id, String name, String status, String plan, LocalDate subscriptionExpiresOn,
                            Instant createdAt, String ownerName, String ownerEmail, long borrowers, long loans,
                            Instant lastLoginAt, long idPhotos, long idPhotoBytes) { }

    public record ReasonRequest(@Size(max = 300, message = "Reason is too long") String reason) { }

    public record SubscriptionRequest(@NotBlank(message = "Choose a plan") String plan, LocalDate expiresOn) { }

    public record CreateLenderRequest(
            @NotBlank(message = "Business name is required")
            @Size(min = 2, max = 120, message = "Business name must be 2 to 120 characters") String businessName,
            @NotBlank(message = "Owner name is required")
            @Size(min = 2, max = 120, message = "Owner name must be 2 to 120 characters") String ownerName,
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 200, message = "Email is too long") String email,
            @NotBlank(message = "Enter a temporary password")
            @Size(max = 100, message = "Password is too long") String password,
            @NotBlank(message = "Choose a plan") String plan,
            LocalDate expiresOn) { }

    public record PasswordRequest(
            @NotBlank(message = "Enter a password")
            @Size(max = 100, message = "Password is too long") String password) { }
}