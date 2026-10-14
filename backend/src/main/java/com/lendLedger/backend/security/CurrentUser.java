package com.lendLedger.backend.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the authenticated user. Services call CurrentUser.tenantId() and pass it to repositories. */
public final class CurrentUser {

    private CurrentUser() { }

    public static LendLedgerPrincipal require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LendLedgerPrincipal principal)) {
            throw new IllegalStateException("No authenticated LendLedger user in this context");
        }
        return principal;
    }

    public static UUID tenantId() { return require().tenantId(); }

    public static UUID userId() { return require().userId(); }
}