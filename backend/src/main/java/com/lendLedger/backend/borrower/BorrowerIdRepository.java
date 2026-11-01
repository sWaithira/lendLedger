package com.lendLedger.backend.borrower;

import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL for ID photos. Every statement filters on tenant_id. */
@Repository
class BorrowerIdRepository {

    record Image(String contentType, byte[] data) { }

    record StatusRow(String kycStatus, Instant verifiedAt, String verifiedBy, LocalDate consentOn, boolean front, boolean back) { }

    private final JdbcClient jdbc;

    BorrowerIdRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<StatusRow> status(UUID tenantId, UUID borrowerId) {
        return jdbc.sql("""
                        SELECT b.kyc_status, b.kyc_verified_at, b.consent_on,
                               (SELECT u.name FROM app_user u WHERE u.tenant_id = b.tenant_id AND u.id = b.kyc_verified_by) AS verified_by,
                               EXISTS (SELECT 1 FROM borrower_id_image i WHERE i.tenant_id = b.tenant_id AND i.borrower_id = b.id AND i.side = 'FRONT') AS front,
                               EXISTS (SELECT 1 FROM borrower_id_image i WHERE i.tenant_id = b.tenant_id AND i.borrower_id = b.id AND i.side = 'BACK') AS back
                        FROM borrower b WHERE b.tenant_id = :tenant AND b.id = :id
                        """)
                .param("tenant", tenantId)
                .param("id", borrowerId)
                .query((rs, n) -> {
                    OffsetDateTime at = rs.getObject("kyc_verified_at", OffsetDateTime.class);
                    return new StatusRow(rs.getString("kyc_status"), at == null ? null : at.toInstant(),
                            rs.getString("verified_by"), rs.getObject("consent_on", LocalDate.class),
                            rs.getBoolean("front"), rs.getBoolean("back"));
                })
                .optional();
    }

    void upsertImage(UUID tenantId, UUID borrowerId, String side, String contentType, byte[] data, UUID userId) {
        jdbc.sql("""
                        INSERT INTO borrower_id_image (tenant_id, borrower_id, side, content_type, size_bytes, data, uploaded_by)
                        VALUES (:tenant, :borrower, :side, :type, :size, :data, :user)
                        ON CONFLICT (tenant_id, borrower_id, side) DO UPDATE
                        SET content_type = EXCLUDED.content_type, size_bytes = EXCLUDED.size_bytes, data = EXCLUDED.data,
                            uploaded_by = EXCLUDED.uploaded_by, uploaded_at = now()
                        """)
                .param("tenant", tenantId)
                .param("borrower", borrowerId)
                .param("side", side)
                .param("type", contentType)
                .param("size", data.length)
                .param("data", data, Types.BINARY)
                .param("user", userId)
                .update();
    }

    Optional<Image> image(UUID tenantId, UUID borrowerId, String side) {
        return jdbc.sql("""
                        SELECT content_type, data FROM borrower_id_image
                        WHERE tenant_id = :tenant AND borrower_id = :borrower AND side = :side
                        """)
                .param("tenant", tenantId)
                .param("borrower", borrowerId)
                .param("side", side)
                .query((rs, n) -> new Image(rs.getString("content_type"), rs.getBytes("data")))
                .optional();
    }

    int deleteImage(UUID tenantId, UUID borrowerId, String side) {
        return jdbc.sql("DELETE FROM borrower_id_image WHERE tenant_id = :tenant AND borrower_id = :borrower AND side = :side")
                .param("tenant", tenantId)
                .param("borrower", borrowerId)
                .param("side", side)
                .update();
    }

    void resetVerification(UUID tenantId, UUID borrowerId) {
        jdbc.sql("""
                        UPDATE borrower SET kyc_status = 'NOT_VERIFIED', kyc_verified_by = NULL, kyc_verified_at = NULL, updated_at = now()
                        WHERE tenant_id = :tenant AND id = :id AND kyc_status <> 'NOT_VERIFIED'
                        """)
                .param("tenant", tenantId)
                .param("id", borrowerId)
                .update();
    }

    void markVerified(UUID tenantId, UUID borrowerId, UUID userId) {
        jdbc.sql("""
                        UPDATE borrower SET kyc_status = 'VERIFIED', kyc_verified_by = :user, kyc_verified_at = now(), updated_at = now()
                        WHERE tenant_id = :tenant AND id = :id
                        """)
                .param("user", userId)
                .param("tenant", tenantId)
                .param("id", borrowerId)
                .update();
    }

    void setConsent(UUID tenantId, UUID borrowerId, LocalDate consentOn) {
        jdbc.sql("UPDATE borrower SET consent_on = :on, updated_at = now() WHERE tenant_id = :tenant AND id = :id")
                .param("on", consentOn, Types.DATE)
                .param("tenant", tenantId)
                .param("id", borrowerId)
                .update();
    }
}