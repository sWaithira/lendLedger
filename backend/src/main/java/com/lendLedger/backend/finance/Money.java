package com.lendLedger.backend.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money is NUMERIC(19,2) in the database and BigDecimal here. Never double, never float. */
final class Money {
    static final int SCALE = 2;

    private Money() { }

    static BigDecimal round(BigDecimal value, RoundingMode mode) { return value.setScale(SCALE, mode); }

    static BigDecimal zero() { return BigDecimal.ZERO.setScale(SCALE); }
}