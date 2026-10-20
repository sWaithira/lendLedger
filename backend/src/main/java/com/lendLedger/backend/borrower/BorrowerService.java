package com.lendLedger.backend.borrower;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.lendLedger.backend.audit.AuditService;
import com.lendLedger.backend.common.ApiException;
import com.lendLedger.backend.common.PageResponse;
import com.lendLedger.backend.security.CurrentUser;
import com.lendLedger.backend.security.LendLedgerPrincipal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BorrowerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BorrowerRepository borrowers;
    private final AuditService audit;

    public BorrowerService(BorrowerRepository borrowers, AuditService audit) {
        this.borrowers = borrowers;
        this.audit = audit;
    }

    /** F-004 / F-005: a matching phone number is a warning, not a block. Proceeding needs a reason. */
    @Transactional
    public BorrowerDtos.Response create(BorrowerDtos.CreateRequest request) {
        LendLedgerPrincipal user = CurrentUser.require();
        String normalized = normalizePhone(request.phone());

        List<Borrower> matches = borrowers.findByTenantIdAndPhoneNormalized(user.tenantId(), normalized);
        String reason = blankToNull(request.duplicateReason());
        if (!matches.isEmpty() && reason == null) {
            throw new ApiException(HttpStatus.CONFLICT, "possible_duplicate",
                    "A borrower with this phone number already exists. Add a reason to save anyway.",
                    Map.of("matches", matches.stream().map(BorrowerDtos.Response::from).toList()));
        }

        Borrower saved = borrowers.save(Borrower.create(user.tenantId(), user.userId(),
                request.fullName().trim(), request.phone().trim(), normalized, blankToNull(request.note())));

        Map<String, String> detail = new LinkedHashMap<>();
        if (!matches.isEmpty()) {
            detail.put("duplicateOf", matches.stream().map(m -> m.getId().toString()).collect(Collectors.joining(",")));
        }
        audit.record("BORROWER_CREATED", "BORROWER", saved.getId(), matches.isEmpty() ? null : reason, detail);
        return BorrowerDtos.Response.from(saved);
    }

    @Transactional(readOnly = true)
    public BorrowerDtos.Response get(UUID id) {
        return BorrowerDtos.Response.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BorrowerDtos.Response> list(String q, BorrowerStatus status, int page, int size) {
        UUID tenantId = CurrentUser.tenantId();
        BorrowerStatus effective = status == null ? BorrowerStatus.ACTIVE : status;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("fullName").ascending().and(Sort.by("id").ascending()));

        String term = sanitize(q);
        Page<Borrower> result;
        if (term.isEmpty()) {
            result = borrowers.findByTenantIdAndStatus(tenantId, effective, pageable);
        } else {
            String pattern = "%" + term.toLowerCase(Locale.ROOT) + "%";
            boolean looksLikePhone = term.matches("[+0-9\\s()\\-]+");
            String digits = looksLikePhone ? PhoneNumbers.searchDigits(term) : "";
            String phonePattern = digits.isEmpty() ? pattern : "%" + digits + "%";
            result = borrowers.search(tenantId, effective, pattern, phonePattern, pageable);
        }
        return PageResponse.of(result.map(BorrowerDtos.Response::from));
    }

    @Transactional
    public BorrowerDtos.Response update(UUID id, BorrowerDtos.UpdateRequest request) {
        Borrower borrower = find(id);
        List<String> changed = borrower.applyChanges(request.fullName().trim(), request.phone().trim(),
                normalizePhone(request.phone()), blankToNull(request.note()));
        if (!changed.isEmpty()) {
            audit.record("BORROWER_UPDATED", "BORROWER", borrower.getId(), null,
                    Map.of("changed", String.join(",", changed)));
        }
        return BorrowerDtos.Response.from(borrower);
    }

    /** A borrower who still owes money cannot be archived; archiving hides, it never deletes. */
    @Transactional
    public BorrowerDtos.Response archive(UUID id) {
        Borrower borrower = find(id);
        if (borrower.getStatus() != BorrowerStatus.ARCHIVED) {
            if (borrowers.countActiveLoans(CurrentUser.tenantId(), id) > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "borrower_has_active_loans",
                        "This borrower still has active loans. Archive them once the loans are paid off or cancelled.");
            }
            borrower.archive();
            audit.record("BORROWER_ARCHIVED", "BORROWER", borrower.getId(), null, Map.of());
        }
        return BorrowerDtos.Response.from(borrower);
    }

    @Transactional
    public BorrowerDtos.Response restore(UUID id) {
        Borrower borrower = find(id);
        if (borrower.getStatus() != BorrowerStatus.ACTIVE) {
            borrower.restore();
            audit.record("BORROWER_RESTORED", "BORROWER", borrower.getId(), null, Map.of());
        }
        return BorrowerDtos.Response.from(borrower);
    }

    /** Another tenant's borrower gives the same 404 as a missing one, so IDs cannot be probed. */
    private Borrower find(UUID id) {
        return borrowers.findByIdAndTenantId(id, CurrentUser.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "borrower_not_found", "Borrower not found"));
    }

    private static String normalizePhone(String raw) {
        try {
            return PhoneNumbers.normalize(raw);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_phone", e.getMessage());
        }
    }

    private static String sanitize(String q) {
        return q == null ? "" : q.trim().replace("%", "").replace("_", "").replace("\\", "");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}