package com.lendLedger.backend.loan;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import com.lendLedger.backend.common.PageResponse;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class DashboardController {

    private final DashboardService service;

    DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    DashboardDtos.Summary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(from, to);
    }

    @GetMapping("/dashboard/collections")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    java.util.List<DashboardDtos.CollectionPoint> collections(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "month") String granularity) {
        return service.collections(from, to, granularity);
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('LOAN_READ')")
    PageResponse<DashboardDtos.OverdueItem> overdue(@RequestParam(defaultValue = "1") int minDays,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return service.overdue(minDays, page, size);
    }

    @GetMapping("/exports/loans.csv")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    ResponseEntity<String> loansCsv() {
        return csv(service.loansCsv());
    }

    @GetMapping("/exports/repayments.csv")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    ResponseEntity<String> repaymentsCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return csv(service.repaymentsCsv(from, to));
    }

    @GetMapping("/exports/overdue.csv")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    ResponseEntity<String> overdueCsv(@RequestParam(defaultValue = "1") int minDays) {
        return csv(service.overdueCsv(minDays));
    }

    private static ResponseEntity<String> csv(DashboardDtos.CsvFile file) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }
}