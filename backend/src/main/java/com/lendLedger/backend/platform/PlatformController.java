package com.lendLedger.backend.platform;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator console API. Only a PLATFORM_ADMIN holds PLATFORM_MANAGE; lender owners and officers get 403. */
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasAuthority('PLATFORM_MANAGE')")
class PlatformController {

    private final PlatformService service;

    PlatformController(PlatformService service) {
        this.service = service;
    }

    @GetMapping("/lenders")
    List<PlatformDtos.LenderRow> lenders() {
        return service.lenders();
    }

    @PostMapping("/lenders")
    List<PlatformDtos.LenderRow> create(@RequestBody @Valid PlatformDtos.CreateLenderRequest body) {
        return service.createLender(body);
    }

    @PostMapping("/lenders/{id}/suspend")
    List<PlatformDtos.LenderRow> suspend(@PathVariable UUID id,
                                         @RequestBody(required = false) @Valid PlatformDtos.ReasonRequest body) {
        return service.suspend(id, body == null ? null : body.reason());
    }

    @PostMapping("/lenders/{id}/reactivate")
    List<PlatformDtos.LenderRow> reactivate(@PathVariable UUID id,
                                            @RequestBody(required = false) @Valid PlatformDtos.ReasonRequest body) {
        return service.reactivate(id, body == null ? null : body.reason());
    }

    @PutMapping("/lenders/{id}/subscription")
    List<PlatformDtos.LenderRow> subscription(@PathVariable UUID id, @RequestBody @Valid PlatformDtos.SubscriptionRequest body) {
        return service.setSubscription(id, body.plan(), body.expiresOn());
    }

    @PostMapping("/lenders/{id}/extend")
    List<PlatformDtos.LenderRow> extend(@PathVariable UUID id) {
        return service.extendOneMonth(id);
    }

    @PostMapping("/lenders/{id}/owner-password")
    List<PlatformDtos.LenderRow> ownerPassword(@PathVariable UUID id, @RequestBody @Valid PlatformDtos.PasswordRequest body) {
        return service.resetOwnerPassword(id, body.password());
    }
}