package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.lendLedger.backend.finance.BalanceComponent;
import com.lendLedger.backend.finance.InterestModel;
import com.lendLedger.backend.finance.LoanTerms;
import com.lendLedger.backend.finance.PeriodUnit;

record LoanRow(UUID id, int loanNumber, UUID borrowerId, String borrowerName, UUID productId, String productName,
               UUID ruleVersionId, int ruleVersionNumber, BigDecimal principal, String currency,
               LocalDate issueDate, int termPeriods, LocalDate dueDate, String status, LocalDate settledOn,
               BigDecimal ratePerPeriod, int graceDays, BigDecimal penaltyRatePerPeriod, BigDecimal penaltyCap,
               List<BalanceComponent> allocationOrder, RoundingMode roundingMode,
               String interestMethod, String periodUnit) {

    LoanTerms terms() {
        InterestModel model = "FIXED_FEE".equals(interestMethod) ? InterestModel.FIXED_FEE : InterestModel.LEGACY_FLAT;
        return new LoanTerms(principal, issueDate, termPeriods, ratePerPeriod, graceDays,
                penaltyRatePerPeriod, penaltyCap, allocationOrder, roundingMode,
                PeriodUnit.valueOf(periodUnit), model);
    }
}