package com.lendLedger.backend.loan;

import com.lendLedger.backend.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
class LoanController {

    private final LoanService service;

    LoanController(LoanService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('LOAN_CREATE')")
    LoanDtos.Preview preview(@Valid @RequestBody LoanDtos.PreviewRequest request) {
        return service.preview(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LOAN_CREATE')")
    LoanDtos.LoanView create(@Valid @RequestBody LoanDtos.CreateRequest request) {
        return service.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LOAN_READ')")
    PageResponse<LoanDtos.LoanView> list(@RequestParam(required = false) UUID borrowerId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(borrowerId, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LOAN_READ')")
    LoanDtos.LoanView get(@PathVariable UUID id) {
        return service.get(id);
    }
}