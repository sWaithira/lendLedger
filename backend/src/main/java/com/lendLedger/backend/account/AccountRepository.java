package com.lendLedger.backend.account;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AccountRepository {

    private final JdbcClient jdbc;

    AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean mustChange(UUID tenantId, UUID userId) {
        return jdbc.sql("SELECT must_change_password FROM app_user WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId)
                .param("id", userId)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }

    /** The date the subscription ended, when it has already passed. Empty for the platform tenant and for lenders in date. */
    Optional<LocalDate> endedOn(UUID tenantId) {
        return jdbc.sql("SELECT subscription_expires_on FROM tenant WHERE id = :id AND NOT is_platform AND subscription_expires_on < current_date")
                .param("id", tenantId)
                .query((rs, i) -> rs.getObject("subscription_expires_on", LocalDate.class))
                .optional();
    }

    Optional<String> hash(UUID tenantId, UUID userId) {
        return jdbc.sql("SELECT password_hash FROM app_user WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId)
                .param("id", userId)
                .query(String.class)
                .optional();
    }

    void setPassword(UUID tenantId, UUID userId, String hash) {
        jdbc.sql("UPDATE app_user SET password_hash = :hash, must_change_password = FALSE WHERE tenant_id = :tenant AND id = :id")
                .param("hash", hash)
                .param("tenant", tenantId)
                .param("id", userId)
                .update();
    }
}