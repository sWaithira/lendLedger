package com.lendLedger.backend.lender;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class RulePreviewCalculatorTest {

    private static void assertTotal(RulePreviewCalculator.Scenario s, String expected) {
        assertThat(s.total()).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void clientTable_3223_at_30_percent() {
        List<RulePreviewCalculator.Scenario> s = RulePreviewCalculator.scenarios(
                new BigDecimal("3223.00"), 1, new BigDecimal("0.30"), 0, BigDecimal.ONE, null);
        assertThat(s).hasSize(3);
        assertThat(s.get(0).label()).isEqualTo("Month 1 (paid on time)");
        assertTotal(s.get(0), "4190");
        assertThat(s.get(0).penalty()).isEqualByComparingTo("0");
        assertTotal(s.get(1), "5157");
        assertTotal(s.get(2), "6124");
    }

    @Test
    void lender_20000_at_30_percent() {
        List<RulePreviewCalculator.Scenario> s = RulePreviewCalculator.scenarios(
                new BigDecimal("20000.00"), 1, new BigDecimal("0.30"), 0, BigDecimal.ONE, null);
        assertTotal(s.get(0), "26000");
        assertTotal(s.get(1), "32000");
        assertTotal(s.get(2), "38000");
    }

    @Test
    void graceDaysDoNotChangeTheAmounts() {
        List<RulePreviewCalculator.Scenario> s = RulePreviewCalculator.scenarios(
                new BigDecimal("1000.00"), 1, new BigDecimal("0.30"), 3, BigDecimal.ONE, null);
        assertTotal(s.get(0), "1300");
        assertTotal(s.get(1), "1600");
        assertTotal(s.get(2), "1900");
    }

    @Test
    void zeroPenaltyMeansNoExtraCharge() {
        List<RulePreviewCalculator.Scenario> s = RulePreviewCalculator.scenarios(
                new BigDecimal("1000.00"), 1, new BigDecimal("0.30"), 0, BigDecimal.ZERO, null);
        assertTotal(s.get(2), "1300");
    }

    @Test
    void penaltyCapLimitsTheTotalPenalty() {
        List<RulePreviewCalculator.Scenario> s = RulePreviewCalculator.scenarios(
                new BigDecimal("1000.00"), 1, new BigDecimal("0.30"), 0, BigDecimal.ONE, new BigDecimal("350"));
        assertTotal(s.get(1), "1600");
        assertTotal(s.get(2), "1650");   // second penalty capped: 300 + 50
    }
}