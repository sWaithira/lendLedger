package com.lendLedger.backend.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum Role {
    OWNER(EnumSet.allOf(Permission.class)),

    // BR-16: no reversal, no audit log, no user management.
    LOAN_OFFICER(EnumSet.of(
            Permission.BORROWER_READ, Permission.BORROWER_WRITE,
            Permission.LOAN_READ, Permission.LOAN_CREATE,
            Permission.REPAYMENT_POST, Permission.REPORT_VIEW));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<Permission> permissions() { return permissions; }
}