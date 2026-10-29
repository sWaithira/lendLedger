package com.lendLedger.backend.borrower;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
@PreAuthorize("hasAuthority('LOAN_CREATE') and hasAuthority('BORROWER_WRITE')")
class ImportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportService service;

    ImportController(ImportService service) {
        this.service = service;
    }

    @GetMapping("/template")
    ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lendledger-import-template.xlsx\"")
                .contentType(XLSX)
                .body(ImportTemplate.build());
    }

    @PostMapping("/preview")
    ImportDtos.Preview preview(@RequestParam("file") MultipartFile file) {
        return service.preview(file);
    }

    @PostMapping("/confirm")
    ImportDtos.Report confirm(@RequestParam("file") MultipartFile file) {
        return service.confirm(file);
    }
}