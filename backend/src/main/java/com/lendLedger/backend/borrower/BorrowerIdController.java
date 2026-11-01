package com.lendLedger.backend.borrower;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** ID photos and verification. Images are served only here, never inside list or detail responses. */
@RestController
@RequestMapping("/api/v1/borrowers/{id}/id")
class BorrowerIdController {

    private final BorrowerIdService service;

    BorrowerIdController(BorrowerIdService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BORROWER_READ')")
    BorrowerIdDtos.IdStatus status(@PathVariable UUID id) {
        return service.status(id);
    }

    @GetMapping("/{side}/image")
    @PreAuthorize("hasAuthority('BORROWER_READ')")
    ResponseEntity<byte[]> image(@PathVariable UUID id, @PathVariable String side) {
        BorrowerIdRepository.Image image = service.image(id, side);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.data());
    }

    @PostMapping("/{side}")
    @PreAuthorize("hasAuthority('BORROWER_WRITE')")
    BorrowerIdDtos.IdStatus upload(@PathVariable UUID id, @PathVariable String side, @RequestParam("file") MultipartFile file) {
        return service.upload(id, side, file);
    }

    @DeleteMapping("/{side}")
    @PreAuthorize("hasAuthority('BORROWER_WRITE')")
    BorrowerIdDtos.IdStatus delete(@PathVariable UUID id, @PathVariable String side) {
        return service.delete(id, side);
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('BORROWER_WRITE')")
    BorrowerIdDtos.IdStatus verify(@PathVariable UUID id) {
        return service.verify(id);
    }

    @PostMapping("/consent")
    @PreAuthorize("hasAuthority('BORROWER_WRITE')")
    BorrowerIdDtos.IdStatus consent(@PathVariable UUID id, @RequestBody @Valid BorrowerIdDtos.ConsentRequest body) {
        return service.setConsent(id, body.consentOn());
    }
}