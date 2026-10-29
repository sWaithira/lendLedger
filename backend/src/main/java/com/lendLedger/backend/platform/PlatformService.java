package com.lendLedger.backend.platform;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.lendLedger.backend.audit.AuditService;
import com.lendLedger.backend.common.ApiException;
import com.lendLedger.backend.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operator actions. Each one is written to the affected lender's own audit log, with the operator as the actor. */
@Service
class PlatformService {

    private static final Set<String> PLANS = Set.of("TRIAL", "STANDARD", "PRO");
    private static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");

    private final PlatformRepository repo;
    private final AuditService audit;
    private final PasswordEncoder encoder;

    PlatformService(PlatformRepository repo, AuditService audit, PasswordEncoder encoder) {
        this.repo = repo;
        this.audit = audit;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    List<PlatformDtos.LenderRow> lenders() {
        return repo.lenders();
    }

    @Transactional
    List<PlatformDtos.LenderRow> suspend(UUID tenantId, String reason) {
        return changeStatus(tenantId, "SUSPENDED", "LENDER_SUSPENDED", reason);
    }

    @Transactional
    List<PlatformDtos.LenderRow> reactivate(UUID tenantId, String reason) {
        return changeStatus(tenantId, "ACTIVE", "LENDER_REACTIVATED", reason);
    }

    @Transactional
    List<PlatformDtos.LenderRow> setSubscription(UUID tenantId, String plan, LocalDate expiresOn) {
        if (!PLANS.contains(plan)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_plan", "Choose Trial, Standard or Pro");
        }
        if (repo.setSubscription(tenantId, plan, expiresOn) == 0) throw notFound();
        audit.recordAs(tenantId, CurrentUser.userId(), "SUBSCRIPTION_CHANGED", "TENANT", tenantId, null,
                Map.of("plan", plan, "expiresOn", expiresOn == null ? "none" : expiresOn.toString()));
        return repo.lenders();
    }

    /** One more month from today, or from the current expiry if that is later. */
    @Transactional
    List<PlatformDtos.LenderRow> extendOneMonth(UUID tenantId) {
        PlatformRepository.Subscription current = repo.subscription(tenantId).orElseThrow(PlatformService::notFound);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate base = (current.expiresOn() != null && current.expiresOn().isAfter(today)) ? current.expiresOn() : today;
        LocalDate next = base.plusMonths(1);
        repo.setSubscription(tenantId, current.plan(), next);
        audit.recordAs(tenantId, CurrentUser.userId(), "SUBSCRIPTION_EXTENDED", "TENANT", tenantId, null,
                Map.of("plan", current.plan(), "expiresOn", next.toString()));
        return repo.lenders();
    }

    /** Sets the owner's password (the lender has no change-password screen yet). The password is never logged or audited. */
    @Transactional
    List<PlatformDtos.LenderRow> resetOwnerPassword(UUID tenantId, String password) {
        if (password.length() < 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "weak_password", "Password must be 10 to 100 characters");
        }
        if (repo.setOwnerPassword(tenantId, encoder.encode(password)) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "owner_not_found", "This lender has no owner account");
        }
        audit.recordAs(tenantId, CurrentUser.userId(), "OWNER_PASSWORD_RESET", "TENANT", tenantId, null, Map.of());
        return repo.lenders();
    }

    private List<PlatformDtos.LenderRow> changeStatus(UUID tenantId, String status, String action, String reason) {
        if (repo.setStatus(tenantId, status) == 0) throw notFound();
        String clean = (reason == null || reason.isBlank()) ? null : reason.trim();
        audit.recordAs(tenantId, CurrentUser.userId(), action, "TENANT", tenantId, clean, Map.of("status", status));
        return repo.lenders();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "lender_not_found", "Lender not found");
    }
}