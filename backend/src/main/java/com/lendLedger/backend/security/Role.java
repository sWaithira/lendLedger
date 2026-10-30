package com.lendLedger.backend.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum Role {
    // Every LENDER permission. PLATFORM_MANAGE is deliberately left out: a lender owner must never operate the platform.
    OWNER(EnumSet.complementOf(EnumSet.of(Permission.PLATFORM_MANAGE))),

    // BR-16: no reversal, no audit log, no user management.
    LOAN_OFFICER(EnumSet.of(
            Permission.BORROWER_READ, Permission.BORROWER_WRITE,
            Permission.LOAN_READ, Permission.LOAN_CREATE,
            Permission.REPAYMENT_POST, Permission.REPORT_VIEW)),

    // The operator of LendLedger. Sees lender metadata only: no borrower, loan or payment permission at all.
    PLATFORM_ADMIN(EnumSet.of(Permission.PLATFORM_MANAGE));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<Permission> permissions() { return permissions; }
}