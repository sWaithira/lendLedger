package com.lendLedger.backend.loan;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RepaymentController {

    private final RepaymentService service;

    RepaymentController(RepaymentService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/loans/{loanId}/repayments/preview")
    @PreAuthorize("hasAuthority('REPAYMENT_POST')")
    RepaymentDtos.RepaymentPreview preview(@PathVariable UUID loanId,
                                           @Valid @RequestBody RepaymentDtos.PreviewRequest request) {
        return service.preview(loanId, request);
    }

    /** 201 for a new payment, 200 when the same Idempotency-Key and request were already processed. */
    @PostMapping("/api/v1/loans/{loanId}/repayments")
    @PreAuthorize("hasAuthority('REPAYMENT_POST')")
    ResponseEntity<RepaymentDtos.PostResult> post(@PathVariable UUID loanId,
                                                  @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                                  @Valid @RequestBody RepaymentDtos.PostRequest request) {
        RepaymentDtos.PostResult result = service.post(loanId, idempotencyKey, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @GetMapping("/api/v1/loans/{loanId}/repayments")
    @PreAuthorize("hasAuthority('LOAN_READ')")
    List<RepaymentDtos.RepaymentView> list(@PathVariable UUID loanId) {
        return service.list(loanId);
    }

    @GetMapping("/api/v1/loans/{loanId}/statement")
    @PreAuthorize("hasAuthority('LOAN_READ')")
    RepaymentDtos.Statement statement(@PathVariable UUID loanId) {
        return service.statement(loanId);
    }

    @PostMapping("/api/v1/repayments/{id}/reverse")
    @PreAuthorize("hasAuthority('REPAYMENT_REVERSE')")
    RepaymentDtos.ReverseResult reverse(@PathVariable UUID id, @Valid @RequestBody RepaymentDtos.ReverseRequest request) {
        return service.reverse(id, request.reason());
    }
}