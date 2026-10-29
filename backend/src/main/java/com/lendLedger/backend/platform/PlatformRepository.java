package com.lendLedger.backend.platform;

import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The only place that reads across lenders, and only metadata and counts. The platform tenant itself is never listed
 * or changed (NOT is_platform).
 */
@Repository
class PlatformRepository {

    record Subscription(String plan, LocalDate expiresOn) { }

    private final JdbcClient jdbc;

    PlatformRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<PlatformDtos.LenderRow> lenders() {
        return jdbc.sql("""
                        SELECT t.id, t.name, t.status, t.plan, t.subscription_expires_on, t.created_at,
                               (SELECT u.name FROM app_user u WHERE u.tenant_id = t.id AND u.role = 'OWNER'
                                ORDER BY u.created_at LIMIT 1) AS owner_name,
                               (SELECT u.email FROM app_user u WHERE u.tenant_id = t.id AND u.role = 'OWNER'
                                ORDER BY u.created_at LIMIT 1) AS owner_email,
                               (SELECT count(*) FROM borrower b WHERE b.tenant_id = t.id) AS borrowers,
                               (SELECT count(*) FROM loan l WHERE l.tenant_id = t.id) AS loans,
                               (SELECT max(u.last_login_at) FROM app_user u WHERE u.tenant_id = t.id) AS last_login
                        FROM tenant t
                        WHERE NOT t.is_platform
                        ORDER BY t.created_at DESC
                        """)
                .query((rs, i) -> new PlatformDtos.LenderRow(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("status"),
                        rs.getString("plan"), rs.getObject("subscription_expires_on", LocalDate.class),
                        instant(rs.getObject("created_at", OffsetDateTime.class)),
                        rs.getString("owner_name"), rs.getString("owner_email"),
                        rs.getLong("borrowers"), rs.getLong("loans"),
                        instant(rs.getObject("last_login", OffsetDateTime.class))))
                .list();
    }

    int setStatus(UUID tenantId, String status) {
        return jdbc.sql("UPDATE tenant SET status = :status WHERE id = :id AND NOT is_platform")
                .param("status", status)
                .param("id", tenantId)
                .update();
    }

    Optional<Subscription> subscription(UUID tenantId) {
        return jdbc.sql("SELECT plan, subscription_expires_on FROM tenant WHERE id = :id AND NOT is_platform")
                .param("id", tenantId)
                .query((rs, i) -> new Subscription(rs.getString("plan"),
                        rs.getObject("subscription_expires_on", LocalDate.class)))
                .optional();
    }

    int setSubscription(UUID tenantId, String plan, LocalDate expiresOn) {
        return jdbc.sql("""
                        UPDATE tenant SET plan = :plan, subscription_expires_on = :expires
                        WHERE id = :id AND NOT is_platform
                        """)
                .param("plan", plan)
                .param("expires", expiresOn, Types.DATE)
                .param("id", tenantId)
                .update();
    }

    int setOwnerPassword(UUID tenantId, String passwordHash) {
        return jdbc.sql("UPDATE app_user SET password_hash = :hash WHERE tenant_id = :tenant AND role = 'OWNER'")
                .param("hash", passwordHash)
                .param("tenant", tenantId)
                .update();
    }

    private static Instant instant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }
}