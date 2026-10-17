package com.lendLedger.backend.audit;

import java.sql.Types;
import java.util.Map;
import java.util.UUID;

import com.lendLedger.backend.security.CurrentUser;
import com.lendLedger.backend.security.LendLedgerPrincipal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit entries (F-018). MANDATORY means it refuses to run outside a transaction, so an entry is
 * always saved or rolled back together with the action it describes: no action without a record,
 * no record of an action that never happened.
 *
 * Keep details free of personal data (names, phone numbers): record WHAT changed, not the values.
 */
@Service
public class AuditService {

    private final JdbcClient jdbc;

    public AuditService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, UUID entityId, String reason, Map<String, String> detail) {
        LendLedgerPrincipal user = CurrentUser.require();
        jdbc.sql("""
                INSERT INTO audit_log (tenant_id, actor_id, action, entity_type, entity_id, reason, detail)
                VALUES (:tenant, :actor, :action, :entityType, :entityId, :reason, CAST(:detail AS jsonb))
                """)
                .param("tenant", user.tenantId())
                .param("actor", user.userId())
                .param("action", action)
                .param("entityType", entityType)
                .param("entityId", entityId == null ? null : entityId.toString(), Types.VARCHAR)
                .param("reason", reason, Types.VARCHAR)
                .param("detail", AuditJson.toJson(detail))
                .update();
    }
}