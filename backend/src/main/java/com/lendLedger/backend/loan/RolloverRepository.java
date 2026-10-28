package com.lendLedger.backend.loan;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL for rollover links. Every query filters on tenant_id (ADR-05). */
@Repository
class RolloverRepository {

    private final JdbcClient jdbc;

    RolloverRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void link(UUID tenantId, UUID newLoanId, UUID fromLoanId) {
        jdbc.sql("UPDATE loan SET rolled_from_loan_id = :from WHERE tenant_id = :tenant AND id = :id")
                .param("from", fromLoanId)
                .param("tenant", tenantId)
                .param("id", newLoanId)
                .update();
    }

    Optional<RolloverDtos.LinkView> rolledFrom(UUID tenantId, UUID loanId) {
        return jdbc.sql("""
                        SELECT f.id, f.loan_number
                        FROM loan l
                        JOIN loan f ON f.tenant_id = l.tenant_id AND f.id = l.rolled_from_loan_id
                        WHERE l.tenant_id = :tenant AND l.id = :id
                        """)
                .param("tenant", tenantId)
                .param("id", loanId)
                .query((rs, n) -> new RolloverDtos.LinkView(rs.getObject("id", UUID.class), rs.getInt("loan_number")))
                .optional();
    }

    Optional<RolloverDtos.LinkView> rolledInto(UUID tenantId, UUID loanId) {
        return jdbc.sql("""
                        SELECT id, loan_number FROM loan
                        WHERE tenant_id = :tenant AND rolled_from_loan_id = :id
                        """)
                .param("tenant", tenantId)
                .param("id", loanId)
                .query((rs, n) -> new RolloverDtos.LinkView(rs.getObject("id", UUID.class), rs.getInt("loan_number")))
                .optional();
    }
}