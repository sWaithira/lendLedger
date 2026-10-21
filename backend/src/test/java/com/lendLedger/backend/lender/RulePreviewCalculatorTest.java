package com.lendLedger.backend.lender;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class RulePreviewCalculatorTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test
    void flatInterestWithNoPenalty() {
        List<RulePreviewCalculator.Scenario> s =
                RulePreviewCalculator.scenarios(d("10000"), 1, d("0.30"), 0, d("0"), null);

        assertThat(s.get(0).total()).isEqualByComparingTo("13000.00");   // on time
        assertThat(s.get(1).total()).isEqualByComparingTo("16000.00");   // one more month of interest
        assertThat(s.get(2).total()).isEqualByComparingTo("22000.00");   // three more
        assertThat(s.get(2).penalty()).isEqualByComparingTo("0.00");
    }

    @Test
    void latePenaltyIsAddedPerMonthOverdue() {
        List<RulePreviewCalculator.Scenario> s =
                RulePreviewCalculator.scenarios(d("10000"), 1, d("0.30"), 0, d("0.05"), null);

        assertThat(s.get(0).penalty()).isEqualByComparingTo("0.00");
        assertThat(s.get(1).penalty()).isEqualByComparingTo("500.00");
        assertThat(s.get(1).total()).isEqualByComparingTo("16500.00");
        assertThat(s.get(2).penalty()).isEqualByComparingTo("1500.00");
        assertThat(s.get(2).total()).isEqualByComparingTo("23500.00");
    }

    @Test
    void penaltyStopsAtTheCap() {
        List<RulePreviewCalculator.Scenario> s =
                RulePreviewCalculator.scenarios(d("10000"), 1, d("0.30"), 0, d("0.05"), d("1000"));

        assertThat(s.get(2).penalty()).isEqualByComparingTo("1000.00");
    }

    @Test
    void graceDaysDoNotChangeTheExample() {
        List<RulePreviewCalculator.Scenario> withGrace =
                RulePreviewCalculator.scenarios(d("10000"), 1, d("0.30"), 5, d("0.05"), null);
        List<RulePreviewCalculator.Scenario> without =
                RulePreviewCalculator.scenarios(d("10000"), 1, d("0.30"), 0, d("0.05"), null);

        assertThat(withGrace.get(2).total()).isEqualByComparingTo(without.get(2).total());
    }

    @Test
    void longerTermsChargeInterestForEveryMonth() {
        List<RulePreviewCalculator.Scenario> s =
                RulePreviewCalculator.scenarios(d("6000"), 3, d("0.10"), 0, d("0"), null);

        assertThat(s.get(0).interest()).isEqualByComparingTo("1800.00");   // 3 months x 600
        assertThat(s.get(0).total()).isEqualByComparingTo("7800.00");
    }

    @Test
    void theLabelsAreInOrder() {
        List<RulePreviewCalculator.Scenario> s =
                RulePreviewCalculator.scenarios(d("1000"), 1, d("0.10"), 0, d("0"), null);

        assertThat(s.get(0).label()).isEqualTo("Paid on time");
        assertThat(s.get(1).label()).isEqualTo("1 month late");
        assertThat(s.get(2).label()).isEqualTo("3 months late");
    }
}