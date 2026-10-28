package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL for the monthly chart. Every query filters on tenant_id (ADR-05). */
@Repository
class MonthlyReportRepository {

    record Lent(LocalDate monthStart, long loans, BigDecimal principal) { }

    record Collected(LocalDate monthStart, BigDecimal principal, BigDecimal interest, BigDecimal penalty) { }

    private final JdbcClient jdbc;

    MonthlyReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<Lent> lent(UUID tenantId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT date_trunc('month', issue_date::timestamp)::date AS month_start,
                               COUNT(*) AS loans,
                               COALESCE(SUM(principal), 0) AS principal
                        FROM loan
                        WHERE tenant_id = :tenant AND status <> 'CANCELLED' AND issue_date BETWEEN :from AND :to
                        GROUP BY 1
                        ORDER BY 1
                        """)
                .param("tenant", tenantId)
                .param("from", from)
                .param("to", to)
                .query((rs, i) -> new Lent(rs.getObject("month_start", LocalDate.class),
                        rs.getLong("loans"), rs.getBigDecimal("principal")))
                .list();
    }

    List<Collected> collected(UUID tenantId, ZoneId zone, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT date_trunc('month', eff.d::timestamp)::date AS month_start,
                               COALESCE(SUM(a.amount) FILTER (WHERE a.component = 'PRINCIPAL'), 0) AS principal,
                               COALESCE(SUM(a.amount) FILTER (WHERE a.component = 'INTEREST'), 0) AS interest,
                               COALESCE(SUM(a.amount) FILTER (WHERE a.component = 'PENALTY'), 0) AS penalty
                        FROM payment_allocation a
                        JOIN repayment r ON r.tenant_id = a.tenant_id AND r.id = a.repayment_id
                        LEFT JOIN repayment_reversal rv ON rv.tenant_id = a.tenant_id AND rv.id = a.reversal_id
                        CROSS JOIN LATERAL (SELECT CASE WHEN a.reversal_id IS NULL THEN r.payment_date
                                                        ELSE (rv.reversed_at AT TIME ZONE :zone)::date END AS d) eff
                        WHERE a.tenant_id = :tenant AND r.method <> 'ROLLOVER' AND eff.d BETWEEN :from AND :to
                        GROUP BY 1
                        ORDER BY 1
                        """)
                .param("zone", zone.getId())
                .param("tenant", tenantId)
                .param("from", from)
                .param("to", to)
                .query((rs, i) -> new Collected(rs.getObject("month_start", LocalDate.class),
                        rs.getBigDecimal("principal"), rs.getBigDecimal("interest"), rs.getBigDecimal("penalty")))
                .list();
    }
}