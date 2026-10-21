package com.lendLedger.backend.lender;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL for lenders, their products and their rule versions. Every settings query filters on tenant_id. */
@Repository
class LenderRepository {

    record TenantRow(String name, String currency, String timezone) { }

    record ProductRow(UUID id, String name, boolean active, BigDecimal minPrincipal, BigDecimal maxPrincipal,
                      int maxTermPeriods) { }

    record RuleRow(UUID id, UUID productId, int versionNumber, String status, LocalDate effectiveFrom,
                   LocalDate effectiveTo, BigDecimal ratePerPeriod, int graceDays, BigDecimal penaltyRatePerPeriod,
                   BigDecimal penaltyCap, String changeReason) { }

    private static final String RULE_SELECT = """
            SELECT id, product_id, version_number, status, effective_from, effective_to, rate_per_period,
                   grace_days, penalty_rate_per_period, penalty_cap, change_reason
            FROM loan_rule_version
            """;

    private final JdbcClient jdbc;

    LenderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ registration

    void insertTenant(UUID id, String name) {
        jdbc.sql("INSERT INTO tenant (id, name) VALUES (:id, :name)")
                .param("id", id)
                .param("name", name)
                .update();
    }

    void insertOwner(UUID id, UUID tenantId, String name, String email, String passwordHash) {
        jdbc.sql("""
                        INSERT INTO app_user (id, tenant_id, name, email, password_hash, role)
                        VALUES (:id, :tenant, :name, :email, :hash, 'OWNER')
                        """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("name", name)
                .param("email", email)
                .param("hash", passwordHash)
                .update();
    }

    void insertProduct(UUID id, UUID tenantId, String name, BigDecimal minPrincipal, BigDecimal maxPrincipal, int maxTermPeriods) {
        jdbc.sql("""
                        INSERT INTO loan_product (id, tenant_id, name, min_principal, max_principal, max_term_periods)
                        VALUES (:id, :tenant, :name, :min, :max, :term)
                        """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("name", name)
                .param("min", minPrincipal)
                .param("max", maxPrincipal)
                .param("term", maxTermPeriods)
                .update();
    }

    /** Inserts an APPROVED FLAT rule version. The actor is both creator and approver (one owner, no second pair of eyes yet). */
    void insertRule(UUID id, UUID tenantId, UUID productId, int versionNumber, LocalDate effectiveFrom,
                    BigDecimal ratePerPeriod, int graceDays, BigDecimal penaltyRatePerPeriod, BigDecimal penaltyCap,
                    UUID actorId, String reason) {
        jdbc.sql("""
                        INSERT INTO loan_rule_version (id, tenant_id, product_id, version_number, status, effective_from,
                               interest_method, rate_per_period, grace_days, penalty_rate_per_period, penalty_cap,
                               created_by, approved_by, approved_at, change_reason)
                        VALUES (:id, :tenant, :product, :version, 'APPROVED', :from,
                                'FLAT', :rate, :grace, :penalty, :cap, :actor, :actor, now(), :reason)
                        """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("product", productId)
                .param("version", versionNumber)
                .param("from", effectiveFrom)
                .param("rate", ratePerPeriod)
                .param("grace", graceDays)
                .param("penalty", penaltyRatePerPeriod)
                .param("cap", penaltyCap, Types.NUMERIC)
                .param("actor", actorId)
                .param("reason", reason, Types.VARCHAR)
                .update();
    }

    // ------------------------------------------------------------------ settings

    Optional<TenantRow> tenant(UUID tenantId) {
        return jdbc.sql("SELECT name, currency, timezone FROM tenant WHERE id = :id")
                .param("id", tenantId)
                .query((rs, n) -> new TenantRow(rs.getString("name"), rs.getString("currency"), rs.getString("timezone")))
                .optional();
    }

    List<ProductRow> products(UUID tenantId) {
        return jdbc.sql("""
                        SELECT id, name, active, min_principal, max_principal, max_term_periods
                        FROM loan_product WHERE tenant_id = :tenant ORDER BY lower(name)
                        """)
                .param("tenant", tenantId)
                .query((rs, n) -> new ProductRow(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getBoolean("active"), rs.getBigDecimal("min_principal"), rs.getBigDecimal("max_principal"),
                        rs.getInt("max_term_periods")))
                .list();
    }

    /** Every rule version of every product of the tenant, newest first within a product. */
    List<RuleRow> rules(UUID tenantId) {
        return jdbc.sql(RULE_SELECT + " WHERE tenant_id = :tenant ORDER BY product_id, version_number DESC")
                .param("tenant", tenantId)
                .query((rs, n) -> new RuleRow(
                        rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class), rs.getInt("version_number"),
                        rs.getString("status"), rs.getObject("effective_from", LocalDate.class),
                        rs.getObject("effective_to", LocalDate.class), rs.getBigDecimal("rate_per_period"),
                        rs.getInt("grace_days"), rs.getBigDecimal("penalty_rate_per_period"),
                        rs.getBigDecimal("penalty_cap"), rs.getString("change_reason")))
                .list();
    }

    /** Locks the product row so two rule changes for one product cannot interleave. */
    Optional<ProductRow> lockProduct(UUID tenantId, UUID productId) {
        return jdbc.sql("""
                        SELECT id, name, active, min_principal, max_principal, max_term_periods
                        FROM loan_product WHERE tenant_id = :tenant AND id = :id FOR UPDATE
                        """)
                .param("tenant", tenantId)
                .param("id", productId)
                .query((rs, n) -> new ProductRow(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getBoolean("active"), rs.getBigDecimal("min_principal"), rs.getBigDecimal("max_principal"),
                        rs.getInt("max_term_periods")))
                .optional();
    }

    Optional<RuleRow> latestRule(UUID tenantId, UUID productId) {
        return jdbc.sql(RULE_SELECT + """
                         WHERE tenant_id = :tenant AND product_id = :product AND status = 'APPROVED'
                         ORDER BY version_number DESC LIMIT 1
                        """)
                .param("tenant", tenantId)
                .param("product", productId)
                .query((rs, n) -> new RuleRow(
                        rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class), rs.getInt("version_number"),
                        rs.getString("status"), rs.getObject("effective_from", LocalDate.class),
                        rs.getObject("effective_to", LocalDate.class), rs.getBigDecimal("rate_per_period"),
                        rs.getInt("grace_days"), rs.getBigDecimal("penalty_rate_per_period"),
                        rs.getBigDecimal("penalty_cap"), rs.getString("change_reason")))
                .optional();
    }

    /** Ends a rule version on a date (exclusive). The database allows this one change to an approved version. */
    void closeRule(UUID tenantId, UUID ruleId, LocalDate endsOn) {
        jdbc.sql("UPDATE loan_rule_version SET effective_to = :to WHERE tenant_id = :tenant AND id = :id")
                .param("to", endsOn)
                .param("tenant", tenantId)
                .param("id", ruleId)
                .update();
    }

    void updateProduct(UUID tenantId, UUID productId, String name, BigDecimal minPrincipal, BigDecimal maxPrincipal,
                       int maxTermPeriods, boolean active) {
        jdbc.sql("""
                        UPDATE loan_product
                        SET name = :name, min_principal = :min, max_principal = :max, max_term_periods = :term, active = :active
                        WHERE tenant_id = :tenant AND id = :id
                        """)
                .param("name", name)
                .param("min", minPrincipal)
                .param("max", maxPrincipal)
                .param("term", maxTermPeriods)
                .param("active", active)
                .param("tenant", tenantId)
                .param("id", productId)
                .update();
    }
}