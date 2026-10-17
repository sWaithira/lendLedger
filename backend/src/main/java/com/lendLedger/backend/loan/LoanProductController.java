package com.lendLedger.backend.loan;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
class LoanProductController {

    private final LoanService service;

    LoanProductController(LoanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LOAN_READ')")
    List<LoanDtos.ProductView> list() {
        return service.products();
    }
}