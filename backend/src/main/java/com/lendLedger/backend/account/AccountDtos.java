package com.lendLedger.backend.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AccountDtos {

    private AccountDtos() { }

    public record Status(boolean mustChangePassword) { }

    public record ChangeRequest(
            @NotBlank(message = "Enter your current password")
            @Size(max = 100, message = "Password is too long") String currentPassword,
            @NotBlank(message = "Enter a new password")
            @Size(max = 100, message = "Password is too long") String newPassword) { }
}