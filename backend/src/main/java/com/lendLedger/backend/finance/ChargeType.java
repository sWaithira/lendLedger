package com.lendLedger.backend.finance;

public enum ChargeType {
    INTEREST(BalanceComponent.INTEREST),
    PENALTY(BalanceComponent.PENALTY);

    private final BalanceComponent component;

    ChargeType(BalanceComponent component) { this.component = component; }

    public BalanceComponent component() { return component; }
}