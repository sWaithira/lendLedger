package com.lendLedger.backend.lender;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class LenderController {

    private final LenderService service;

    LenderController(LenderService service) {
        this.service = service;
    }

    /** Public: a new lender creating their account. */
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> register(@Valid @RequestBody LenderDtos.RegisterRequest request) {
        service.register(request);
        return Map.of("status", "created");
    }

    /** Public: a pure calculation, so people can see what rules mean before they register. */
    @PostMapping("/rules/preview")
    LenderDtos.PreviewResponse preview(@Valid @RequestBody LenderDtos.PreviewRequest request) {
        return service.preview(request);
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    LenderDtos.SettingsView settings() {
        return service.settings();
    }

    @PostMapping("/settings/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    LenderDtos.SettingsView addProduct(@Valid @RequestBody LenderDtos.ProductInput request) {
        return service.addProduct(request);
    }

    @PutMapping("/settings/products/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    LenderDtos.SettingsView updateProduct(@PathVariable UUID id, @Valid @RequestBody LenderDtos.ProductUpdate request) {
        return service.updateProduct(id, request);
    }

    @PostMapping("/settings/products/{id}/rules")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    LenderDtos.SettingsView addRule(@PathVariable UUID id, @Valid @RequestBody LenderDtos.NewRuleRequest request) {
        return service.addRule(id, request);
    }
}