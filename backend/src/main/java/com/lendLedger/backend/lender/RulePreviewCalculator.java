package com.lendLedger.backend.lender;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lendLedger.backend.finance.AssessedCharge;
import com.lendLedger.backend.finance.ChargeAssessor;
import com.lendLedger.backend.finance.InterestModel;
import com.lendLedger.backend.finance.LedgerBalance;
import com.lendLedger.backend.finance.LoanTerms;
import com.lendLedger.backend.finance.Outstanding;
import com.lendLedger.backend.finance.PeriodUnit;

/**
 * Shows a lender what their rules mean in money, using the SAME engine that prices real loans, so the example
 * can never disagree with an actual loan. Nothing is saved. Uses the fixed-fee model that new rules get.
 */
public final class RulePreviewCalculator {

    public record Scenario(String label, BigDecimal interest, BigDecimal penalty, BigDecimal total) { }

    private static final LocalDate EXAMPLE_ISSUE_DATE = LocalDate.of(2026, 1, 15);

    private RulePreviewCalculator() { }

    public static List<Scenario> scenarios(BigDecimal principal, int termMonths, BigDecimal ratePerPeriod,
                                           int graceDays, BigDecimal penaltyPerPeriod, BigDecimal penaltyCap) {
        LoanTerms terms = new LoanTerms(principal, EXAMPLE_ISSUE_DATE, termMonths, ratePerPeriod, graceDays,
                penaltyPerPeriod, penaltyCap, LoanTerms.DEFAULT_ORDER, RoundingMode.HALF_UP,
                PeriodUnit.MONTH, InterestModel.FIXED_FEE);
        List<Scenario> result = new ArrayList<>();
        result.add(scenario("Paid on time", terms, terms.dueDate()));
        result.add(scenario("1 month overdue (1 penalty)", terms, ChargeAssessor.penaltyDate(terms, 1)));
        result.add(scenario("2 months overdue (2 penalties)", terms, ChargeAssessor.penaltyDate(terms, 2)));
        return result;
    }

    private static Scenario scenario(String label, LoanTerms terms, LocalDate asOf) {
        List<AssessedCharge> charges = ChargeAssessor.assess(terms, asOf);
        Outstanding owed = LedgerBalance.compute(terms, charges, Map.of());
        return new Scenario(label, owed.interest(), owed.penalty(), owed.total());
    }
}