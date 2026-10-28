package com.lendLedger.backend.loan;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RepaymentDtos {

    private RepaymentDtos() { }

    public record PreviewRequest(
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be positive")
            @Digits(integer = 15, fraction = 2, message = "Use at most 2 decimal places") BigDecimal amount,
            @NotNull(message = "Payment date is required") LocalDate paymentDate) { }

    public record PostRequest(
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be positive")
            @Digits(integer = 15, fraction = 2, message = "Use at most 2 decimal places") BigDecimal amount,
            @NotNull(message = "Payment date is required") LocalDate paymentDate,
            @NotNull(message = "Choose a payment method") PaymentMethod method,
            @Size(max = 100, message = "Reference is too long") String reference) { }

    public record ReverseRequest(
            @NotBlank(message = "A reason is required")
            @Size(min = 3, max = 500, message = "Reason must be 3 to 500 characters") String reason) { }

    public record AllocationLine(String component, BigDecimal amount) { }

    public record RepaymentPreview(LocalDate paymentDate, BigDecimal amount, LoanDtos.BalanceView before,
                                   List<AllocationLine> allocations, LoanDtos.BalanceView after,
                                   boolean settlesLoan) { }

    public record RepaymentView(UUID id, UUID loanId, BigDecimal amount, LocalDate paymentDate, Instant recordedAt,
                                String method, String reference, List<AllocationLine> allocations,
                                boolean reversed, Instant reversedAt, String reversalReason) { }

    /** replayed = true means this was a retry of a request that had already been processed. */
    public record PostResult(RepaymentView repayment, LoanDtos.LoanView loan, boolean replayed) { }

    public record ReverseResult(RepaymentView repayment, LoanDtos.LoanView loan) { }

    public record StatementEntry(LocalDate date, String type, String description, BigDecimal amount,
                                 BigDecimal balanceAfter, UUID repaymentId) { }

    public record Statement(LoanDtos.LoanView loan, List<StatementEntry> entries) { }
}