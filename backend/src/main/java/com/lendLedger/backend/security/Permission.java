package com.lendLedger.backend.security;

/** What a user may do. Controllers check these, not role names (BR-16). */
public enum Permission {
    BORROWER_READ, BORROWER_WRITE,
    LOAN_READ, LOAN_CREATE,
    REPAYMENT_POST, REPAYMENT_REVERSE,
    REPORT_VIEW,
    AUDIT_VIEW,
    USER_MANAGE,
    SETTINGS_MANAGE
}