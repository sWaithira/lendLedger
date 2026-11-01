package com.lendLedger.backend.borrower;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.lendLedger.backend.audit.AuditService;
import com.lendLedger.backend.common.ApiException;
import com.lendLedger.backend.common.TenantClock;
import com.lendLedger.backend.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** ID photos for a borrower. The lender reviews the photos and marks the ID verified; a photo alone verifies nothing. */
@Service
class BorrowerIdService {

    private static final long MAX_BYTES = 1024 * 1024;

    private final BorrowerIdRepository repo;
    private final AuditService audit;
    private final TenantClock clock;

    BorrowerIdService(BorrowerIdRepository repo, AuditService audit, TenantClock clock) {
        this.repo = repo;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    BorrowerIdDtos.IdStatus status(UUID borrowerId) {
        return statusOf(CurrentUser.tenantId(), borrowerId);
    }

    @Transactional
    BorrowerIdDtos.IdStatus upload(UUID borrowerId, String sideRaw, MultipartFile file) {
        UUID tenantId = CurrentUser.tenantId();
        String side = side(sideRaw);
        statusOf(tenantId, borrowerId);   // 404 unless the borrower belongs to this lender
        if (file == null || file.isEmpty()) throw bad("invalid_image", "Choose a photo first.");
        if (file.getSize() > MAX_BYTES) throw bad("image_too_large", "That photo is too large. Use a smaller one.");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw bad("invalid_image", "That photo could not be read. Try again.");
        }
        String type = sniff(bytes);
        if (type == null) throw bad("invalid_image", "Use a JPEG, PNG or WebP photo.");

        repo.upsertImage(tenantId, borrowerId, side, type, bytes, CurrentUser.userId());
        repo.resetVerification(tenantId, borrowerId);
        audit.record("ID_IMAGE_UPLOADED", "BORROWER", borrowerId, null, Map.of("side", side));
        return statusOf(tenantId, borrowerId);
    }

    /** Not read-only: every view is written to the audit log. */
    @Transactional
    BorrowerIdRepository.Image image(UUID borrowerId, String sideRaw) {
        UUID tenantId = CurrentUser.tenantId();
        String side = side(sideRaw);
        BorrowerIdRepository.Image image = repo.image(tenantId, borrowerId, side)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "id_image_not_found", "No photo on file"));
        audit.record("ID_IMAGE_VIEWED", "BORROWER", borrowerId, null, Map.of("side", side));
        return image;
    }

    @Transactional
    BorrowerIdDtos.IdStatus delete(UUID borrowerId, String sideRaw) {
        UUID tenantId = CurrentUser.tenantId();
        String side = side(sideRaw);
        statusOf(tenantId, borrowerId);
        if (repo.deleteImage(tenantId, borrowerId, side) > 0) {
            repo.resetVerification(tenantId, borrowerId);
            audit.record("ID_IMAGE_DELETED", "BORROWER", borrowerId, null, Map.of("side", side));
        }
        return statusOf(tenantId, borrowerId);
    }

    @Transactional
    BorrowerIdDtos.IdStatus verify(UUID borrowerId) {
        UUID tenantId = CurrentUser.tenantId();
        BorrowerIdDtos.IdStatus current = statusOf(tenantId, borrowerId);
        if (!current.front() && !current.back()) {
            throw new ApiException(HttpStatus.CONFLICT, "no_id_photos", "Add an ID photo before marking it verified.");
        }
        repo.markVerified(tenantId, borrowerId, CurrentUser.userId());
        audit.record("ID_VERIFIED", "BORROWER", borrowerId, null, Map.of());
        return statusOf(tenantId, borrowerId);
    }

    @Transactional
    BorrowerIdDtos.IdStatus setConsent(UUID borrowerId, LocalDate consentOn) {
        UUID tenantId = CurrentUser.tenantId();
        statusOf(tenantId, borrowerId);
        if (consentOn != null && consentOn.isAfter(clock.today())) {
            throw bad("consent_date_in_future", "The consent date cannot be in the future.");
        }
        repo.setConsent(tenantId, borrowerId, consentOn);
        audit.record("CONSENT_DATE_SET", "BORROWER", borrowerId, null, Map.of());
        return statusOf(tenantId, borrowerId);
    }

    // ------------------------------------------------------------------ helpers

    private BorrowerIdDtos.IdStatus statusOf(UUID tenantId, UUID borrowerId) {
        BorrowerIdRepository.StatusRow r = repo.status(tenantId, borrowerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "borrower_not_found", "Borrower not found"));
        String status = "VERIFIED".equals(r.kycStatus()) ? "VERIFIED" : (r.front() || r.back() ? "ON_FILE" : "NONE");
        return new BorrowerIdDtos.IdStatus(status, r.front(), r.back(), r.verifiedAt(), r.verifiedBy(), r.consentOn());
    }

    private static String side(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (s.equals("front")) return "FRONT";
        if (s.equals("back")) return "BACK";
        throw bad("invalid_side", "Choose front or back.");
    }

    /** The type comes from the file's first bytes, never from its name or the header the browser sent. */
    private static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "image/png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}