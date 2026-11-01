package com.lendLedger.backend.lender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response shapes for registration, rule previews and lender settings. */
public final class LenderDtos {

    private LenderDtos() { }

    /** Percentages as a person types them (30 means 30%). The server converts them to fractions. */
    public record RuleInput(
            @NotNull(message = "Interest rate is required")
            @DecimalMin(value = "0.01", message = "Interest must be at least 0.01%")
            @DecimalMax(value = "100", message = "Interest cannot be more than 100%")
            @Digits(integer = 3, fraction = 4, message = "Use at most 4 decimal places for the interest rate") BigDecimal ratePercent,
            @NotNull(message = "Grace period is required")
            @Min(value = 0, message = "Grace period cannot be negative")
            @Max(value = 60, message = "Grace period cannot be more than 60 days") Integer graceDays,
            @NotNull(message = "Late penalty is required")
            @DecimalMin(value = "0", message = "Late penalty cannot be negative")
            @DecimalMax(value = "300", message = "Late penalty cannot be more than 300%")
            @Digits(integer = 3, fraction = 4, message = "Use at most 4 decimal places for the late penalty") BigDecimal penaltyPercent,
            @DecimalMin(value = "0", message = "Penalty limit cannot be negative")
            @Digits(integer = 15, fraction = 2, message = "Use at most 2 decimal places for the penalty limit") BigDecimal penaltyCap) { }

    public record ProductInput(
            @NotBlank(message = "Product name is required")
            @Size(min = 2, max = 80, message = "Product name must be 2 to 80 characters") String name,
            @NotNull(message = "Smallest loan is required")
            @DecimalMin(value = "1", message = "Smallest loan must be at least 1")
            @Digits(integer = 15, fraction = 2, message = "Use at most 2 decimal places for the smallest loan") BigDecimal minPrincipal,
            @NotNull(message = "Largest loan is required")
            @DecimalMin(value = "1", message = "Largest loan must be at least 1")
            @Digits(integer = 15, fraction = 2, message = "Use at most 2 decimal places for the largest loan") BigDecimal maxPrincipal,
            @NotNull(message = "Longest term is required")
            @Min(value = 1, message = "Longest term must be at least 1 month")
            @Max(value = 60, message = "Longest term cannot be more than 60 months") Integer maxTermPeriods,
            @NotNull(message = "Lending rules are required") @Valid RuleInput rule) { }

    public record ProductUpdate(
            @NotBlank(message = "Product name is required")
            @Size(min = 2, max = 80, message = "Product name must be 2 to 80 characters") String name,
            @NotNull(message = "Smallest loan is required")
            @DecimalMin(value = "1", message = "Smallest loan must be at least 1")
            @Digits(integer = 15, fraction = 2, message = "Use at most 2 decimal places for the smallest loan") BigDecimal minPrincipal,
            @NotNull(message = "Largest loan is required")
            @DecimalMin(value = "1", message = "Largest loan must be at least 1")
            @Digits(integer = 15, fraction = 2, message = "Use at most 2 decimal places for the largest loan") BigDecimal maxPrincipal,
            @NotNull(message = "Longest term is required")
            @Min(value = 1, message = "Longest term must be at least 1 month")
            @Max(value = 60, message = "Longest term cannot be more than 60 months") Integer maxTermPeriods,
            @NotNull(message = "Choose whether this product is offered") Boolean active) { }

    public record RegisterRequest(
            @NotBlank(message = "Business name is required")
            @Size(min = 2, max = 120, message = "Business name must be 2 to 120 characters") String businessName,
            @NotBlank(message = "Your name is required")
            @Size(min = 2, max = 120, message = "Your name must be 2 to 120 characters") String ownerName,
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 200, message = "Email is too long") String email,
            @NotBlank(message = "Password is required")
            @Size(max = 100, message = "Password is too long") String password,
            @Size(max = 100, message = "Access code is too long") String accessCode,
            @NotNull(message = "Your first product is required") @Valid ProductInput product) { }

    public record NewRuleRequest(
            @NotNull(message = "Lending rules are required") @Valid RuleInput rule,
            @NotNull(message = "Choose a start date") LocalDate effectiveFrom,
            @Size(max = 300, message = "Reason is too long") String reason) { }

    public record PreviewRequest(
            @NotNull(message = "Example amount is required")
            @DecimalMin(value = "1", message = "Example amount must be at least 1")
            @Digits(integer = 12, fraction = 2, message = "Use at most 2 decimal places for the example amount") BigDecimal principal,
            @NotNull(message = "Example term is required")
            @Min(value = 1, message = "Example term must be at least 1 month")
            @Max(value = 60, message = "Example term cannot be more than 60 months") Integer termMonths,
            @NotNull(message = "Lending rules are required") @Valid RuleInput rule) { }

    public record PreviewResponse(List<RulePreviewCalculator.Scenario> scenarios) { }

    public record RuleView(UUID id, int versionNumber, String status, LocalDate effectiveFrom, LocalDate effectiveTo,
                           BigDecimal ratePerPeriod, int graceDays, BigDecimal penaltyRatePerPeriod,
                           BigDecimal penaltyCap, String changeReason) { }

    public record ProductView(UUID id, String name, boolean active, BigDecimal minPrincipal, BigDecimal maxPrincipal,
                              int maxTermPeriods, RuleView currentRule, List<RuleView> history) { }

    public record SettingsView(String businessName, String currency, String timezone, List<ProductView> products) { }
}