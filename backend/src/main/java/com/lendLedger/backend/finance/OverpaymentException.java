package com.lendLedger.backend.finance;

import java.math.BigDecimal;

/** BR-09: a payment larger than the amount outstanding is rejected, never silently split. */
public class OverpaymentException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final BigDecimal payment;
    private final BigDecimal outstanding;

    public OverpaymentException(BigDecimal payment, BigDecimal outstanding) {
        super("Payment " + payment + " exceeds the outstanding amount " + outstanding);
        this.payment = payment;
        this.outstanding = outstanding;
    }

    public BigDecimal payment() { return payment; }
    public BigDecimal outstanding() { return outstanding; }
}