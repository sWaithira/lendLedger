package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL for the performance card. Every query filters on tenant_id (ADR-05). Loans made by a rollover are left out.
 * The optional product and payment-method filters add a clause only when they are set.
 */
@Repository
class PerformanceRepository {

    record Lent(long loans, BigDecimal principal) { }

    record Received(BigDecimal principal, BigDecimal interest, BigDecimal penalty) { }

    record Completed(long paidOff, long rolledOver) { }

    private final JdbcClient jdbc;

    PerformanceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String productClause(UUID productId) {
        return productId == null ? "" : " AND l.product_id = :product";
    }

    private static String methodClause(String method) {
        return method == null ? "" : " AND r.method = :method";
    }

    private static JdbcClient.StatementSpec filters(JdbcClient.StatementSpec spec, UUID productId, String method) {
        JdbcClient.StatementSpec s = spec;
        if (productId != null) s = s.param("product", productId);
        if (method != null) s = s.param("method", method);
        return s;
    }

    Lent lent(UUID tenantId, UUID productId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT count(*) AS n, COALESCE(SUM(l.principal), 0) AS total
                FROM loan l
                WHERE l.tenant_id = :tenant AND l.issue_date BETWEEN :from AND :to
                  AND l.status <> 'CANCELLED' AND l.rolled_from_loan_id IS NULL
                """ + productClause(productId);
        return filters(jdbc.sql(sql).param("tenant", tenantId).param("from", from).param("to", to), productId, null)
                .query((rs, i) -> new Lent(rs.getLong("n"), rs.getBigDecimal("total")))
                .single();
    }

    /** Money received in the period by component. A reversal counts on the day it was recorded; rollovers are not money. */
    Received received(UUID tenantId, ZoneId zone, LocalDate from, LocalDate to, UUID productId, String method) {
        String sql = """
                SELECT COALESCE(SUM(a.amount) FILTER (WHERE a.component = 'PRINCIPAL'), 0) AS principal,
                       COALESCE(SUM(a.amount) FILTER (WHERE a.component = 'INTEREST'), 0) AS interest,
                       COALESCE(SUM(a.amount) FILTER (WHERE a.component = 'PENALTY'), 0) AS penalty
                FROM payment_allocation a
                JOIN repayment r ON r.tenant_id = a.tenant_id AND r.id = a.repayment_id
                JOIN loan l ON l.tenant_id = a.tenant_id AND l.id = a.loan_id
                LEFT JOIN repayment_reversal rv ON rv.tenant_id = a.tenant_id AND rv.id = a.reversal_id
                CROSS JOIN LATERAL (SELECT CASE WHEN a.reversal_id IS NULL THEN r.payment_date
                                                ELSE (rv.reversed_at AT TIME ZONE :zone)::date END AS d) eff
                WHERE a.tenant_id = :tenant AND r.method <> 'ROLLOVER' AND eff.d BETWEEN :from AND :to
                """ + productClause(productId) + methodClause(method);
        return filters(jdbc.sql(sql).param("zone", zone.getId()).param("tenant", tenantId).param("from", from).param("to", to),
                productId, method)
                .query((rs, i) -> new Received(rs.getBigDecimal("principal"), rs.getBigDecimal("interest"), rs.getBigDecimal("penalty")))
                .single();
    }

    /** Money received per payment method in the period, net of reversals. Rollovers are not money. */
    List<PerformanceDtos.MethodTotal> receivedByMethod(UUID tenantId, ZoneId zone, LocalDate from, LocalDate to,
                                                       UUID productId, String method) {
        String sql = """
                SELECT r.method, COALESCE(SUM(a.amount), 0) AS total
                FROM payment_allocation a
                JOIN repayment r ON r.tenant_id = a.tenant_id AND r.id = a.repayment_id
                JOIN loan l ON l.tenant_id = a.tenant_id AND l.id = a.loan_id
                LEFT JOIN repayment_reversal rv ON rv.tenant_id = a.tenant_id AND rv.id = a.reversal_id
                CROSS JOIN LATERAL (SELECT CASE WHEN a.reversal_id IS NULL THEN r.payment_date
                                                ELSE (rv.reversed_at AT TIME ZONE :zone)::date END AS d) eff
                WHERE a.tenant_id = :tenant AND r.method <> 'ROLLOVER' AND eff.d BETWEEN :from AND :to
                """ + productClause(productId) + methodClause(method) + """
                 GROUP BY r.method
                 HAVING SUM(a.amount) <> 0
                 ORDER BY SUM(a.amount) DESC
                """;
        return filters(jdbc.sql(sql).param("zone", zone.getId()).param("tenant", tenantId).param("from", from).param("to", to),
                productId, method)
                .query((rs, i) -> new PerformanceDtos.MethodTotal(rs.getString("method"), rs.getBigDecimal("total")))
                .list();
    }

    /** Closed loans issued in the period: paid off, or closed because their principal was rolled into a new loan. */
    Completed completed(UUID tenantId, UUID productId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT count(*) FILTER (WHERE NOT EXISTS (
                           SELECT 1 FROM loan c WHERE c.tenant_id = l.tenant_id AND c.rolled_from_loan_id = l.id)) AS paid_off,
                       count(*) FILTER (WHERE EXISTS (
                           SELECT 1 FROM loan c WHERE c.tenant_id = l.tenant_id AND c.rolled_from_loan_id = l.id)) AS rolled_over
                FROM loan l
                WHERE l.tenant_id = :tenant AND l.status = 'COMPLETED' AND l.rolled_from_loan_id IS NULL
                  AND l.issue_date BETWEEN :from AND :to
                """ + productClause(productId);
        return filters(jdbc.sql(sql).param("tenant", tenantId).param("from", from).param("to", to), productId, null)
                .query((rs, i) -> new Completed(rs.getLong("paid_off"), rs.getLong("rolled_over")))
                .single();
    }

    /** Open loans in the period that were made by a rollover, so the outcome counts match the lent figure. */
    Set<UUID> activeRolledChildren(UUID tenantId, LocalDate from, LocalDate to) {
        return new HashSet<>(jdbc.sql("""
                        SELECT id FROM loan
                        WHERE tenant_id = :tenant AND status = 'ACTIVE' AND rolled_from_loan_id IS NOT NULL
                          AND issue_date BETWEEN :from AND :to
                        """)
                .param("tenant", tenantId)
                .param("from", from)
                .param("to", to)
                .query((rs, i) -> rs.getObject("id", UUID.class))
                .list());
    }

    /** Ids of the loans of one product issued in the period, used to filter the open-loan outcome counts. */
    Set<UUID> loanIdsForProduct(UUID tenantId, UUID productId, LocalDate from, LocalDate to) {
        return new HashSet<>(jdbc.sql("""
                        SELECT l.id FROM loan l
                        WHERE l.tenant_id = :tenant AND l.product_id = :product AND l.issue_date BETWEEN :from AND :to
                        """)
                .param("tenant", tenantId)
                .param("product", productId)
                .param("from", from)
                .param("to", to)
                .query((rs, i) -> rs.getObject("id", UUID.class))
                .list());
    }
}