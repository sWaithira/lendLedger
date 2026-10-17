package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.lendLedger.backend.finance.BalanceComponent;
import com.lendLedger.backend.finance.LoanTerms;

/** A loan joined with the (immutable) rule version it was written under. */
record LoanRow(UUID id, UUID borrowerId, String borrowerName, UUID productId, String productName,
               UUID ruleVersionId, int ruleVersionNumber, BigDecimal principal, String currency,
               LocalDate issueDate, int termPeriods, LocalDate dueDate, String status, LocalDate settledOn,
               BigDecimal ratePerPeriod, int graceDays, BigDecimal penaltyRatePerPeriod, BigDecimal penaltyCap,
               List<BalanceComponent> allocationOrder, RoundingMode roundingMode) {

    LoanTerms terms() {
        return new LoanTerms(principal, issueDate, termPeriods, ratePerPeriod, graceDays,
                penaltyRatePerPeriod, penaltyCap, allocationOrder, roundingMode);
    }
}