package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read-only reporting queries. Every query filters on tenant_id (ADR-05). */
@Repository
class DashboardRepository {

    record CollectionRow(LocalDate periodStart, BigDecimal total, BigDecimal principal,
                         BigDecimal interest, BigDecimal penalty) { }

    record Disbursement(long count, BigDecimal total) { }

    record Contact(String phone, String email) { }

    record PaymentRow(LocalDate paymentDate, Instant recordedAt, UUID loanId, int loanNumber, String borrowerName,
                      BigDecimal amount, String method, String reference, BigDecimal principal,
                      BigDecimal interest, BigDecimal penalty, Instant reversedAt, String reversalReason) { }

    private record StatusCount(String status, long n) { }

    private record ContactRow(UUID id, Contact contact) { }

    private record LastPayment(UUID loanId, LocalDate date) { }

    private final JdbcClient jdbc;

    DashboardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Map<String, Long> loanCountsByStatus(UUID tenantId) {
        List<StatusCount> rows = jdbc.sql("SELECT status, count(*) AS n FROM loan WHERE tenant_id = :tenant GROUP BY status")
                .param("tenant", tenantId)
                .query((rs, i) -> new StatusCount(rs.getString("status"), rs.getLong("n")))
                .list();
        Map<String, Long> counts = new HashMap<>();
        rows.forEach(r -> counts.put(r.status(), r.n()));
        return counts;
    }

    Disbursement disbursed(UUID tenantId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT count(*) AS n, COALESCE(SUM(principal), 0) AS total
                        FROM loan
                        WHERE tenant_id = :tenant AND issue_date BETWEEN :from AND :to AND status <> 'CANCELLED' AND rolled_from_loan_id IS NULL
                        """)
                .param("tenant", tenantId)
                .param("from", from)
                .param("to", to)
                .query((rs, i) -> new Disbursement(rs.getLong("n"), rs.getBigDecimal("total")))
                .single();
    }

    /** Money collected per period by component. A reversal counts on the day it was recorded, so closed periods never change. */
    List<CollectionRow> collections(UUID tenantId, ZoneId zone, LocalDate from, LocalDate to, String unit) {
        return jdbc.sql("""
                        SELECT date_trunc(:unit, eff.d::timestamp)::date AS period_start,
                               COALESCE(SUM(a.amount), 0) AS total,
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
                .param("unit", unit)
                .param("zone", zone.getId())
                .param("tenant", tenantId)
                .param("from", from)
                .param("to", to)
                .query((rs, i) -> new CollectionRow(rs.getObject("period_start", LocalDate.class),
                        rs.getBigDecimal("total"), rs.getBigDecimal("principal"),
                        rs.getBigDecimal("interest"), rs.getBigDecimal("penalty")))
                .list();
    }

    /** Borrower id to phone (digits only) and email. */
    Map<UUID, Contact> borrowerContacts(UUID tenantId) {
        List<ContactRow> rows = jdbc.sql("SELECT id, phone_normalized, email FROM borrower WHERE tenant_id = :tenant")
                .param("tenant", tenantId)
                .query((rs, i) -> new ContactRow(rs.getObject("id", UUID.class),
                        new Contact(rs.getString("phone_normalized"), rs.getString("email"))))
                .list();
        Map<UUID, Contact> contacts = new HashMap<>();
        rows.forEach(r -> contacts.put(r.id(), r.contact()));
        return contacts;
    }

    /** Loan id to the date of its latest payment that has not been reversed. */
    Map<UUID, LocalDate> lastPaymentDates(UUID tenantId) {
        List<LastPayment> rows = jdbc.sql("""
                        SELECT r.loan_id, max(r.payment_date) AS last_payment
                        FROM repayment r
                        WHERE r.tenant_id = :tenant
                          AND NOT EXISTS (SELECT 1 FROM repayment_reversal rv
                                          WHERE rv.tenant_id = r.tenant_id AND rv.repayment_id = r.id)
                        GROUP BY r.loan_id
                        """)
                .param("tenant", tenantId)
                .query((rs, i) -> new LastPayment(rs.getObject("loan_id", UUID.class),
                        rs.getObject("last_payment", LocalDate.class)))
                .list();
        Map<UUID, LocalDate> last = new HashMap<>();
        rows.forEach(r -> last.put(r.loanId(), r.date()));
        return last;
    }

    /** Every payment dated in the range, with its original split and reversal (if any). */
    List<PaymentRow> payments(UUID tenantId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT r.payment_date, r.recorded_at, r.loan_id, l.loan_number, b.full_name, r.amount, r.method, r.reference,
                               COALESCE(SUM(a.amount) FILTER (WHERE a.component = 'PRINCIPAL' AND a.reversal_id IS NULL), 0) AS principal,
                               COALESCE(SUM(a.amount) FILTER (WHERE a.component = 'INTEREST'  AND a.reversal_id IS NULL), 0) AS interest,
                               COALESCE(SUM(a.amount) FILTER (WHERE a.component = 'PENALTY'   AND a.reversal_id IS NULL), 0) AS penalty,
                               rv.reversed_at, rv.reason AS reversal_reason
                        FROM repayment r
                        JOIN loan l ON l.tenant_id = r.tenant_id AND l.id = r.loan_id
                        JOIN borrower b ON b.tenant_id = l.tenant_id AND b.id = l.borrower_id
                        LEFT JOIN repayment_reversal rv ON rv.tenant_id = r.tenant_id AND rv.repayment_id = r.id
                        LEFT JOIN payment_allocation a ON a.tenant_id = r.tenant_id AND a.repayment_id = r.id
                        WHERE r.tenant_id = :tenant AND r.payment_date BETWEEN :from AND :to AND r.method <> 'ROLLOVER'
                        GROUP BY r.id, l.id, b.id, rv.id
                        ORDER BY r.payment_date, r.recorded_at
                        """)
                .param("tenant", tenantId)
                .param("from", from)
                .param("to", to)
                .query((rs, i) -> new PaymentRow(
                        rs.getObject("payment_date", LocalDate.class), instant(rs.getObject("recorded_at", OffsetDateTime.class)),
                        rs.getObject("loan_id", UUID.class), rs.getInt("loan_number"), rs.getString("full_name"),
                        rs.getBigDecimal("amount"), rs.getString("method"), rs.getString("reference"),
                        rs.getBigDecimal("principal"), rs.getBigDecimal("interest"), rs.getBigDecimal("penalty"),
                        instant(rs.getObject("reversed_at", OffsetDateTime.class)), rs.getString("reversal_reason")))
                .list();
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}