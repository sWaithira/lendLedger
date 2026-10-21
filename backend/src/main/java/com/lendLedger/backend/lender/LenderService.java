package com.lendLedger.backend.lender;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lendLedger.backend.audit.AuditService;
import com.lendLedger.backend.common.ApiException;
import com.lendLedger.backend.common.TenantClock;
import com.lendLedger.backend.security.CurrentUser;
import com.lendLedger.backend.security.LendLedgerPrincipal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LenderService {

    /** A lender's first rules apply from the beginning of time, so past loans can be entered under them. */
    private static final LocalDate FIRST_RULES_START = LocalDate.of(2000, 1, 1);

    private final LenderRepository repo;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final TenantClock clock;
    private final boolean registrationEnabled;
    private final String accessCode;

    public LenderService(LenderRepository repo, PasswordEncoder encoder, AuditService audit, TenantClock clock,
                         @Value("${lendledger.registration.enabled:true}") boolean registrationEnabled,
                         @Value("${lendledger.registration.access-code:}") String accessCode) {
        this.repo = repo;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
        this.registrationEnabled = registrationEnabled;
        this.accessCode = accessCode == null ? "" : accessCode;
    }

    // ------------------------------------------------------------------ registration (nobody is signed in)

    /** Creates the lender, its owner, its first product and that product's first approved rules, all or nothing. */
    @Transactional
    public void register(LenderDtos.RegisterRequest request) {
        if (!registrationEnabled) {
            throw new ApiException(HttpStatus.FORBIDDEN, "registration_closed",
                    "Registration is closed. Ask the administrator for an account.");
        }
        if (!accessCode.isBlank() && !sameSecret(accessCode, request.accessCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "invalid_access_code", "That access code is not valid.");
        }
        String email = request.email().trim();
        if (request.password().equalsIgnoreCase(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "weak_password", "The password cannot be the same as the email.");
        }
        checkLimits(request.product().minPrincipal(), request.product().maxPrincipal());

        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        try {
            repo.insertTenant(tenantId, request.businessName().trim());
            repo.insertOwner(ownerId, tenantId, request.ownerName().trim(), email, encoder.encode(request.password()));
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "email_taken", "An account with this email already exists. Sign in instead.");
        }
        createProduct(tenantId, ownerId, request.product(), "Initial rules at registration");
        audit.recordAs(tenantId, ownerId, "LENDER_REGISTERED", "TENANT", tenantId, null,
                Map.of("business", request.businessName().trim()));
    }

    /** What the rules mean in money, from the same engine that prices real loans. Saves nothing. */
    public LenderDtos.PreviewResponse preview(LenderDtos.PreviewRequest request) {
        try {
            return new LenderDtos.PreviewResponse(RulePreviewCalculator.scenarios(
                    request.principal(), request.termMonths(), fraction(request.rule().ratePercent()),
                    request.rule().graceDays(), fraction(request.rule().penaltyPercent()), request.rule().penaltyCap()));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_rules", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ settings (owner only)

    @Transactional(readOnly = true)
    public LenderDtos.SettingsView settings() {
        UUID tenantId = CurrentUser.tenantId();
        LocalDate today = clock.today();
        LenderRepository.TenantRow tenant = repo.tenant(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "lender_not_found", "Lender not found"));

        Map<UUID, List<LenderDtos.RuleView>> rulesByProduct = new HashMap<>();
        for (LenderRepository.RuleRow r : repo.rules(tenantId)) {
            rulesByProduct.computeIfAbsent(r.productId(), k -> new ArrayList<>()).add(toView(r));
        }
        List<LenderDtos.ProductView> products = new ArrayList<>();
        for (LenderRepository.ProductRow p : repo.products(tenantId)) {
            List<LenderDtos.RuleView> history = rulesByProduct.getOrDefault(p.id(), List.of());
            LenderDtos.RuleView current = history.stream().filter(r -> isCurrent(r, today)).findFirst().orElse(null);
            products.add(new LenderDtos.ProductView(p.id(), p.name(), p.active(), p.minPrincipal(), p.maxPrincipal(),
                    p.maxTermPeriods(), current, history));
        }
        return new LenderDtos.SettingsView(tenant.name(), tenant.currency(), tenant.timezone(), products);
    }

    @Transactional
    public LenderDtos.SettingsView addProduct(LenderDtos.ProductInput input) {
        LendLedgerPrincipal user = CurrentUser.require();
        checkLimits(input.minPrincipal(), input.maxPrincipal());
        UUID productId = createProduct(user.tenantId(), user.userId(), input, "Initial rules");
        audit.record("PRODUCT_CREATED", "PRODUCT", productId, null, Map.of());
        return settings();
    }

    @Transactional
    public LenderDtos.SettingsView updateProduct(UUID productId, LenderDtos.ProductUpdate update) {
        UUID tenantId = CurrentUser.tenantId();
        repo.lockProduct(tenantId, productId).orElseThrow(RuntimeExceptions::productNotFound);
        checkLimits(update.minPrincipal(), update.maxPrincipal());
        try {
            repo.updateProduct(tenantId, productId, update.name().trim(), update.minPrincipal(), update.maxPrincipal(),
                    update.maxTermPeriods(), update.active());
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "product_name_taken", "You already have a product with that name.");
        }
        audit.record("PRODUCT_UPDATED", "PRODUCT", productId, null, Map.of("active", String.valueOf(update.active())));
        return settings();
    }

    /**
     * A rule change is a NEW version that starts on a date. The previous version ends that day, loans issued earlier
     * keep the rules they were created with, and the database refuses overlapping versions.
     */
    @Transactional
    public LenderDtos.SettingsView addRule(UUID productId, LenderDtos.NewRuleRequest request) {
        LendLedgerPrincipal user = CurrentUser.require();
        UUID tenantId = user.tenantId();
        repo.lockProduct(tenantId, productId).orElseThrow(RuntimeExceptions::productNotFound);

        if (request.effectiveFrom().isBefore(clock.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "effective_date_in_past",
                    "The start date cannot be in the past. Loans already issued keep the rules they were created with.");
        }
        LenderRepository.RuleRow latest = repo.latestRule(tenantId, productId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "no_current_rules", "This product has no rules to change."));
        if (!request.effectiveFrom().isAfter(latest.effectiveFrom())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "effective_date_not_after_current",
                    "The new rules must start after the current rules start (" + latest.effectiveFrom() + "). Choose a later date.");
        }

        String reason = blankToNull(request.reason());
        repo.closeRule(tenantId, latest.id(), request.effectiveFrom());
        repo.insertRule(UUID.randomUUID(), tenantId, productId, latest.versionNumber() + 1, request.effectiveFrom(),
                fraction(request.rule().ratePercent()), request.rule().graceDays(), fraction(request.rule().penaltyPercent()),
                request.rule().penaltyCap(), user.userId(), reason);
        audit.record("RULE_VERSION_CREATED", "PRODUCT", productId, reason, Map.of(
                "version", String.valueOf(latest.versionNumber() + 1),
                "effectiveFrom", request.effectiveFrom().toString()));
        return settings();
    }

    // ------------------------------------------------------------------ helpers

    private UUID createProduct(UUID tenantId, UUID actorId, LenderDtos.ProductInput p, String reason) {
        UUID productId = UUID.randomUUID();
        try {
            repo.insertProduct(productId, tenantId, p.name().trim(), p.minPrincipal(), p.maxPrincipal(), p.maxTermPeriods());
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "product_name_taken", "You already have a product with that name.");
        }
        repo.insertRule(UUID.randomUUID(), tenantId, productId, 1, FIRST_RULES_START, fraction(p.rule().ratePercent()),
                p.rule().graceDays(), fraction(p.rule().penaltyPercent()), p.rule().penaltyCap(), actorId, reason);
        return productId;
    }

    private static void checkLimits(BigDecimal min, BigDecimal max) {
        if (max.compareTo(min) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_limits", "The largest loan must be at least the smallest loan.");
        }
    }

    /** 30 (percent) becomes 0.30. */
    private static BigDecimal fraction(BigDecimal percent) {
        return percent.movePointLeft(2);
    }

    private static boolean isCurrent(LenderDtos.RuleView r, LocalDate today) {
        return "APPROVED".equals(r.status()) && !r.effectiveFrom().isAfter(today)
                && (r.effectiveTo() == null || r.effectiveTo().isAfter(today));
    }

    private static LenderDtos.RuleView toView(LenderRepository.RuleRow r) {
        return new LenderDtos.RuleView(r.id(), r.versionNumber(), r.status(), r.effectiveFrom(), r.effectiveTo(),
                r.ratePerPeriod(), r.graceDays(), r.penaltyRatePerPeriod(), r.penaltyCap(), r.changeReason());
    }

    private static boolean sameSecret(String expected, String given) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = (given == null ? "" : given).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static final class RuntimeExceptions {
        static ApiException productNotFound() {
            return new ApiException(HttpStatus.NOT_FOUND, "product_not_found", "Product not found");
        }
    }
}