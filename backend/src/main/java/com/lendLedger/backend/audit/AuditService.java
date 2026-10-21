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

    /** Records an action by the signed-in user. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, UUID entityId, String reason, Map<String, String> detail) {
        LendLedgerPrincipal user = CurrentUser.require();
        recordAs(user.tenantId(), user.userId(), action, entityType, entityId, reason, detail);
    }

    /** Records an action by a named person, for moments when nobody is signed in yet (registration). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(UUID tenantId, UUID actorId, String action, String entityType, UUID entityId,
                         String reason, Map<String, String> detail) {
        jdbc.sql("""
                INSERT INTO audit_log (tenant_id, actor_id, action, entity_type, entity_id, reason, detail)
                VALUES (:tenant, :actor, :action, :entityType, :entityId, :reason, CAST(:detail AS jsonb))
                """)
                .param("tenant", tenantId)
                .param("actor", actorId)
                .param("action", action)
                .param("entityType", entityType)
                .param("entityId", entityId == null ? null : entityId.toString(), Types.VARCHAR)
                .param("reason", reason, Types.VARCHAR)
                .param("detail", AuditJson.toJson(detail))
                .update();
    }
}