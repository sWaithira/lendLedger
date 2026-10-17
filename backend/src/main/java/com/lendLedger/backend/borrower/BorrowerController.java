package com.lendLedger.backend.borrower;

import java.util.UUID;

import com.lendLedger.backend.common.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP only: permission checks and request validation here, business rules in the service. */
@RestController
@RequestMapping("/api/v1/borrowers")
class BorrowerController {

    private final BorrowerService service;

    BorrowerController(BorrowerService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BORROWER_WRITE')")
    BorrowerDtos.Response create(@Valid @RequestBody BorrowerDtos.CreateRequest request) {
        return service.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BORROWER_READ')")
    PageResponse<BorrowerDtos.Response> list(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) BorrowerStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.list(q, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BORROWER_READ')")
    BorrowerDtos.Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BORROWER_WRITE')")
    BorrowerDtos.Response update(@PathVariable UUID id, @Valid @RequestBody BorrowerDtos.UpdateRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('BORROWER_WRITE')")
    BorrowerDtos.Response archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('BORROWER_WRITE')")
    BorrowerDtos.Response restore(@PathVariable UUID id) {
        return service.restore(id);
    }
}