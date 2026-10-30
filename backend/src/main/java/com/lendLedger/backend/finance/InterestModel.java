package com.lendLedger.backend.finance;

/** How a loan's charges are calculated. Chosen by the rule version, so old loans never change. */
public enum InterestModel {
    /** Original engine: interest per term period, plus interest and penalty per overdue month. */
    LEGACY_FLAT,
    /** Interest once at issue; overdue penalties equal to a share of that interest, one per overdue month. */
    FIXED_FEE
}