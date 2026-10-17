package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.lendLedger.backend.finance.BalanceComponent;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL for repayments. Every query filters on tenant_id (ADR-05). Ledger rows are only ever inserted. */
@Repository
class RepaymentRepository {

    record AllocationRow(UUID repaymentId, BalanceComponent component, BigDecimal amount) { }

    private static final String SELECT = """
            SELECT r.id, r.loan_id, r.amount, r.currency, r.payment_date, r.recorded_at, r.method, r.reference,
                   r.idempotency_key, r.request_hash, r.actor_id, rv.reversed_at, rv.reason AS reversal_reason
            FROM repayment r
            LEFT JOIN repayment_reversal rv ON rv.tenant_id = r.tenant_id AND rv.repayment_id = r.id
            """;

    private static final String NOT_REVERSED = """
            NOT EXISTS (SELECT 1 FROM repayment_reversal rv
                        WHERE rv.tenant_id = r.tenant_id AND rv.repayment_id = r.id)
            """;

    private final JdbcClient jdbc;

    RepaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ reads

    Optional<RepaymentRow> findById(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " WHERE r.tenant_id = :tenant AND r.id = :id")
                .param("tenant", tenantId)
                .param("id", id)
                .query(RepaymentRepository::map)
                .optional();
    }

    Optional<RepaymentRow> findByIdempotencyKey(UUID tenantId, String key) {
        return jdbc.sql(SELECT + " WHERE r.tenant_id = :tenant AND r.idempotency_key = :key")
                .param("tenant", tenantId)
                .param("key", key)
                .query(RepaymentRepository::map)
                .optional();
    }

    List<RepaymentRow> listByLoan(UUID tenantId, UUID loanId) {
        return jdbc.sql(SELECT + " WHERE r.tenant_id = :tenant AND r.loan_id = :loan ORDER BY r.payment_date, r.recorded_at, r.id")
                .param("tenant", tenantId)
                .param("loan", loanId)
                .query(RepaymentRepository::map)
                .list();
    }

    /** The payment date of the most recent payment that has not been reversed. */
    Optional<LocalDate> latestActivePaymentDate(UUID tenantId, UUID loanId) {
        return jdbc.sql("SELECT r.payment_date FROM repayment r WHERE r.tenant_id = :tenant AND r.loan_id = :loan AND "
                        + NOT_REVERSED + " ORDER BY r.payment_date DESC, r.recorded_at DESC, r.id DESC LIMIT 1")
                .param("tenant", tenantId)
                .param("loan", loanId)
                .query(LocalDate.class)
                .optional();
    }

    Optional<UUID> latestActiveRepaymentId(UUID tenantId, UUID loanId) {
        return jdbc.sql("SELECT r.id FROM repayment r WHERE r.tenant_id = :tenant AND r.loan_id = :loan AND "
                        + NOT_REVERSED + " ORDER BY r.payment_date DESC, r.recorded_at DESC, r.id DESC LIMIT 1")
                .param("tenant", tenantId)
                .param("loan", loanId)
                .query(UUID.class)
                .optional();
    }

    /** The allocations as originally posted (not the negative mirror rows). */
    List<AllocationRow> originalAllocations(UUID tenantId, Collection<UUID> repaymentIds) {
        if (repaymentIds.isEmpty()) return List.of();
        return jdbc.sql("""
                        SELECT repayment_id, component, amount
                        FROM payment_allocation
                        WHERE tenant_id = :tenant AND repayment_id IN (:ids) AND reversal_id IS NULL
                        ORDER BY repayment_id, sequence_no
                        """)
                .param("tenant", tenantId)
                .param("ids", repaymentIds)
                .query((rs, n) -> new AllocationRow(rs.getObject("repayment_id", UUID.class),
                        BalanceComponent.valueOf(rs.getString("component")), rs.getBigDecimal("amount")))
                .list();
    }

    // ------------------------------------------------------------------ writes (insert only)

    void insertRepayment(UUID id, UUID tenantId, UUID loanId, BigDecimal amount, String currency,
                         LocalDate paymentDate, String method, String reference, String idempotencyKey,
                         String requestHash, UUID actorId) {
        // clock_timestamp() (not now()) so "latest payment" follows the order payments were really processed
        // under the loan lock, not the order their transactions happened to start.
        jdbc.sql("""
                        INSERT INTO repayment (id, tenant_id, loan_id, amount, currency, payment_date, recorded_at,
                                               method, reference, idempotency_key, request_hash, actor_id)
                        VALUES (:id, :tenant, :loan, :amount, :currency, :paymentDate, clock_timestamp(),
                                :method, :reference, :key, :hash, :actor)
                        """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("loan", loanId)
                .param("amount", amount)
                .param("currency", currency)
                .param("paymentDate", paymentDate)
                .param("method", method)
                .param("reference", reference, Types.VARCHAR)
                .param("key", idempotencyKey)
                .param("hash", requestHash)
                .param("actor", actorId)
                .update();
    }

    void insertAllocation(UUID tenantId, UUID repaymentId, UUID loanId, String component, BigDecimal amount, int sequence) {
        jdbc.sql("""
                        INSERT INTO payment_allocation (tenant_id, repayment_id, loan_id, component, amount, sequence_no)
                        VALUES (:tenant, :repayment, :loan, :component, :amount, :sequence)
                        """)
                .param("tenant", tenantId)
                .param("repayment", repaymentId)
                .param("loan", loanId)
                .param("component", component)
                .param("amount", amount)
                .param("sequence", sequence)
                .update();
    }

    void insertReversal(UUID id, UUID tenantId, UUID repaymentId, UUID loanId, String reason, UUID actorId) {
        jdbc.sql("""
                        INSERT INTO repayment_reversal (id, tenant_id, repayment_id, loan_id, reason, actor_id)
                        VALUES (:id, :tenant, :repayment, :loan, :reason, :actor)
                        """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("repayment", repaymentId)
                .param("loan", loanId)
                .param("reason", reason)
                .param("actor", actorId)
                .update();
    }

    /** Copies the original allocations with the sign flipped, so the net of the two is zero. */
    void insertMirrorAllocations(UUID tenantId, UUID repaymentId, UUID reversalId) {
        jdbc.sql("""
                        INSERT INTO payment_allocation (tenant_id, repayment_id, loan_id, reversal_id, component, amount, sequence_no)
                        SELECT tenant_id, repayment_id, loan_id, :reversal, component, -amount, sequence_no
                        FROM payment_allocation
                        WHERE tenant_id = :tenant AND repayment_id = :repayment AND reversal_id IS NULL
                        """)
                .param("reversal", reversalId)
                .param("tenant", tenantId)
                .param("repayment", repaymentId)
                .update();
    }

    // ------------------------------------------------------------------ mapping

    private static RepaymentRow map(ResultSet rs, int rowNum) throws SQLException {
        return new RepaymentRow(
                rs.getObject("id", UUID.class), rs.getObject("loan_id", UUID.class), rs.getBigDecimal("amount"),
                rs.getString("currency"), rs.getObject("payment_date", LocalDate.class),
                instant(rs, "recorded_at"), rs.getString("method"), rs.getString("reference"),
                rs.getString("idempotency_key"), rs.getString("request_hash"), rs.getObject("actor_id", UUID.class),
                instant(rs, "reversed_at"), rs.getString("reversal_reason"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}