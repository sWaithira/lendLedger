package com.lendLedger.backend.security;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** DEV ONLY and TEMPORARY: proves that permission checks are enforced on the server. */
@Profile("dev")
@RestController
@RequestMapping("/api/v1/dev")
class DevProbeController {

    @GetMapping("/owner-only")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    Map<String, String> ownerOnly() {
        return Map.of("result", "you hold USER_MANAGE", "tenant", CurrentUser.tenantId().toString());
    }
}