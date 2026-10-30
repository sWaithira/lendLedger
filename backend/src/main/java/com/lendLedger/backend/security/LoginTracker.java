package com.lendLedger.backend.security;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Records each successful sign-in so the platform console can show when a lender was last active. */
@Component
class LoginTracker {

    private final JdbcClient jdbc;

    LoginTracker(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof LendLedgerPrincipal user) {
            jdbc.sql("UPDATE app_user SET last_login_at = now() WHERE tenant_id = :tenant AND id = :id")
                    .param("tenant", user.tenantId())
                    .param("id", user.userId())
                    .update();
        }
    }
}