package com.lendLedger.backend.finance;

import java.math.BigDecimal;

/** How much of one payment was applied to one component. */
public record Allocation(BalanceComponent component, BigDecimal amount) { }