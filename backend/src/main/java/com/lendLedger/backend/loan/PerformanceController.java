package com.lendLedger.backend.loan;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
class PerformanceController {

    private final PerformanceService service;

    PerformanceController(PerformanceService service) {
        this.service = service;
    }

    @GetMapping("/performance")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    PerformanceDtos.Performance performance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String method) {
        return service.performance(from, to, productId, method);
    }
}