package com.lendLedger.backend.account;

import java.util.Map;
import java.util.UUID;

import com.lendLedger.backend.audit.AuditService;
import com.lendLedger.backend.common.ApiException;
import com.lendLedger.backend.security.CurrentUser;
import com.lendLedger.backend.security.PasswordPolicy;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountService {

    private final AccountRepository repo;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    AccountService(AccountRepository repo, PasswordEncoder encoder, AuditService audit) {
        this.repo = repo;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    AccountDtos.Status status() {
        return new AccountDtos.Status(repo.mustChange(CurrentUser.tenantId(), CurrentUser.userId()));
    }

    @Transactional
    void changePassword(AccountDtos.ChangeRequest request) {
        UUID tenantId = CurrentUser.tenantId();
        UUID userId = CurrentUser.userId();
        String hash = repo.hash(tenantId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user_not_found", "User not found"));
        if (!encoder.matches(request.currentPassword(), hash)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "wrong_password", "Your current password is not correct.");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "same_password", "Choose a different password from the current one.");
        }
        PasswordPolicy.require(request.newPassword(), CurrentUser.require().email());
        repo.setPassword(tenantId, userId, encoder.encode(request.newPassword()));
        audit.record("PASSWORD_CHANGED", "USER", userId, null, Map.of());
    }
}