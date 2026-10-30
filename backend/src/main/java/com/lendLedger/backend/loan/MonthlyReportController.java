package com.lendLedger.backend.loan;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
class MonthlyReportController {

    private final MonthlyReportService service;

    MonthlyReportController(MonthlyReportService service) {
        this.service = service;
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    List<MonthlyReportDtos.MonthPoint> monthly(
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return service.monthly(months, end);
    }
}