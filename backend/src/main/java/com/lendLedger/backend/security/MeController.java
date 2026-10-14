package com.lendLedger.backend.security;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class MeController {

    record MeResponse(UUID userId, UUID tenantId, String name, String email, String role, List<String> permissions) { }

    @GetMapping("/me")
    MeResponse me() {
        LendLedgerPrincipal user = CurrentUser.require();
        List<String> permissions = user.role().permissions().stream()
                .map(Enum::name).sorted(Comparator.naturalOrder()).toList();
        return new MeResponse(user.userId(), user.tenantId(), user.name(), user.email(),
                user.role().name(), permissions);
    }
}