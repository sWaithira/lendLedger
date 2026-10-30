package com.lendLedger.backend.borrower;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL for imports. Every query filters on tenant_id (ADR-05). */
@Repository
class ImportRepository {

    private final JdbcClient jdbc;

    ImportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean batchExists(UUID tenantId, String fileHash) {
        Long n = jdbc.sql("SELECT count(*) FROM import_batch WHERE tenant_id = :tenant AND file_hash = :hash")
                .param("tenant", tenantId)
                .param("hash", fileHash)
                .query(Long.class)
                .single();
        return n != null && n > 0;
    }

    void insertBatch(UUID id, UUID tenantId, String fileHash, String fileName, int borrowers, int loans, UUID actorId) {
        jdbc.sql("""
                        INSERT INTO import_batch (id, tenant_id, file_hash, file_name, borrowers_created, loans_created, created_by)
                        VALUES (:id, :tenant, :hash, :name, :borrowers, :loans, :actor)
                        """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("hash", fileHash)
                .param("name", fileName)
                .param("borrowers", borrowers)
                .param("loans", loans)
                .param("actor", actorId)
                .update();
    }

    /** True when this borrower (by phone) already has a loan with the same amount and issue date. */
    boolean loanExists(UUID tenantId, String phoneNormalized, BigDecimal principal, LocalDate issueDate) {
        Long n = jdbc.sql("""
                        SELECT count(*)
                        FROM loan l
                        JOIN borrower b ON b.tenant_id = l.tenant_id AND b.id = l.borrower_id
                        WHERE l.tenant_id = :tenant AND b.phone_normalized = :phone
                          AND l.principal = :principal AND l.issue_date = :date AND l.status <> 'CANCELLED'
                        """)
                .param("tenant", tenantId)
                .param("phone", phoneNormalized)
                .param("principal", principal)
                .param("date", issueDate)
                .query(Long.class)
                .single();
        return n != null && n > 0;
    }
}