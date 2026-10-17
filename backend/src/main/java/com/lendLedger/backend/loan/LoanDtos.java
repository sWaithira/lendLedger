package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Request and response shapes for the loan API. All money arithmetic happens on the server. */
public final class LoanDtos {

    private LoanDtos() { }

    public record PreviewRequest(
            @NotNull(message = "Choose a product") UUID productId,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be positive")
            @Digits(integer = 15, fraction = 2, message = "Use at most 2 decimal places") BigDecimal principal,
            @NotNull(message = "Issue date is required") LocalDate issueDate,
            @Min(value = 1, message = "Term must be at least 1 month")
            @Max(value = 60, message = "Term cannot exceed 60 months") int termPeriods) { }

    public record CreateRequest(
            @NotNull(message = "Choose a borrower") UUID borrowerId,
            @NotNull(message = "Choose a product") UUID productId,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be positive")
            @Digits(integer = 15, fraction = 2, message = "Use at most 2 decimal places") BigDecimal principal,
            @NotNull(message = "Issue date is required") LocalDate issueDate,
            @Min(value = 1, message = "Term must be at least 1 month")
            @Max(value = 60, message = "Term cannot exceed 60 months") int termPeriods) { }

    public record ChargeLine(String type, String kind, int period, LocalDate effectiveDate, BigDecimal amount) { }

    public record Preview(UUID ruleVersionId, int ruleVersionNumber, BigDecimal ratePerPeriod,
                          BigDecimal principal, LocalDate issueDate, LocalDate dueDate, int termPeriods,
                          List<ChargeLine> charges, BigDecimal totalInterest, BigDecimal totalPayable) { }

    public record BalanceView(BigDecimal principal, BigDecimal interest, BigDecimal penalty, BigDecimal total) { }

    public record LoanView(UUID id, UUID borrowerId, String borrowerName, UUID productId, String productName,
                           BigDecimal principal, String currency, LocalDate issueDate, int termPeriods,
                           LocalDate dueDate, BigDecimal ratePerPeriod, int ruleVersionNumber,
                           String status, String displayStatus, long daysOverdue,
                           LocalDate asOf, BalanceView outstanding) { }

    public record ProductView(UUID id, String name, String currency, BigDecimal minPrincipal,
                              BigDecimal maxPrincipal, UUID ruleVersionId, int ruleVersionNumber,
                              BigDecimal ratePerPeriod, int graceDays, BigDecimal penaltyRatePerPeriod) { }
}