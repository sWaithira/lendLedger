package com.lendLedger.backend.loan;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
class RolloverController {

    private final RolloverService service;

    RolloverController(RolloverService service) {
        this.service = service;
    }

    @PostMapping("/{id}/rollover")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LOAN_CREATE')")
    LoanDtos.LoanView rollover(@PathVariable UUID id, @RequestBody(required = false) @Valid RolloverDtos.Request request) {
        return service.rollover(id, request == null ? new RolloverDtos.Request(null, null) : request);
    }

    @GetMapping("/{id}/rollover-links")
    @PreAuthorize("isAuthenticated()")
    RolloverDtos.Links links(@PathVariable UUID id) {
        return service.linksFor(id);
    }
}