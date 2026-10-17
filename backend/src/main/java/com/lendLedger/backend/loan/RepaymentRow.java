package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A repayment with its reversal (if any). reversedAt is null for a payment that stands. */
record RepaymentRow(UUID id, UUID loanId, BigDecimal amount, String currency, LocalDate paymentDate,
                    Instant recordedAt, String method, String reference, String idempotencyKey,
                    String requestHash, UUID actorId, Instant reversedAt, String reversalReason) { }