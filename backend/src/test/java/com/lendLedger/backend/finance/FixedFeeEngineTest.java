package com.lendLedger.backend.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class FixedFeeEngineTest {

    private static BigDecimal due(LoanTerms t, LocalDate asOf) {
        return t.principal().add(ChargeAssessor.assess(t, asOf).stream()
                .map(AssessedCharge::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static void assertDue(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void clientTable_3223_at_30_percent() {
        LoanTerms t = LoanTerms.fixedFee(new BigDecimal("3223.00"), LocalDate.of(2026, 10, 25), 1,
                PeriodUnit.MONTH, new BigDecimal("0.30"));
        assertThat(t.dueDate()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertDue(due(t, LocalDate.of(2026, 10, 25)), "4190");   // same day
        assertDue(due(t, LocalDate.of(2026, 11, 25)), "4190");   // due date itself
        assertDue(due(t, LocalDate.of(2026, 11, 26)), "5157");   // day after due date
        assertDue(due(t, LocalDate.of(2026, 12, 25)), "6124");
        assertDue(due(t, LocalDate.of(2027, 1, 25)), "7091");
    }

    @Test
    void clientExample_1000_at_30_percent() {
        LoanTerms t = LoanTerms.fixedFee(new BigDecimal("1000.00"), LocalDate.of(2026, 10, 1), 1,
                PeriodUnit.MONTH, new BigDecimal("0.30"));
        assertDue(due(t, LocalDate.of(2026, 10, 1)), "1300");
        assertDue(due(t, LocalDate.of(2026, 11, 1)), "1300");
        assertDue(due(t, LocalDate.of(2026, 11, 2)), "1600");
        assertDue(due(t, LocalDate.of(2026, 12, 1)), "1900");
        assertDue(due(t, LocalDate.of(2027, 1, 1)), "2200");
    }

    @Test
    void interestIsChargedOnceHoweverLongTheTerm() {
        LoanTerms t = LoanTerms.fixedFee(new BigDecimal("1000.00"), LocalDate.of(2026, 10, 1), 3,
                PeriodUnit.MONTH, new BigDecimal("0.30"));
        assertDue(due(t, LocalDate.of(2026, 10, 1)), "1300");
    }

    @Test
    void dayTermWithGrace() {
        LoanTerms t = LoanTerms.fixedFee(new BigDecimal("1000.00"), LocalDate.of(2026, 10, 1), 7,
                PeriodUnit.DAY, new BigDecimal("0.30")).withGraceDays(3);
        assertThat(t.dueDate()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(t.overdueStart()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertDue(due(t, LocalDate.of(2026, 10, 9)), "1300");    // in grace
        assertDue(due(t, LocalDate.of(2026, 10, 11)), "1300");   // last day of grace
        assertDue(due(t, LocalDate.of(2026, 10, 12)), "1600");
    }

    @Test
    void weekTerm() {
        LoanTerms t = LoanTerms.fixedFee(new BigDecimal("1000.00"), LocalDate.of(2026, 10, 1), 2,
                PeriodUnit.WEEK, new BigDecimal("0.30"));
        assertThat(t.dueDate()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void penaltyCapIsCumulative() {
        LoanTerms t = LoanTerms.fixedFee(new BigDecimal("1000.00"), LocalDate.of(2026, 10, 1), 1,
                PeriodUnit.MONTH, new BigDecimal("0.30")).withPenalty(BigDecimal.ONE, new BigDecimal("450"));
        assertDue(due(t, LocalDate.of(2026, 12, 1)), "1750");    // 1000 + 300 interest + 300 + 150 penalties (cap 450)
    }

    @Test
    void penaltyMonthCountMatchesCharges() {
        LoanTerms t = LoanTerms.fixedFee(new BigDecimal("1000.00"), LocalDate.of(2026, 10, 1), 1,
                PeriodUnit.MONTH, new BigDecimal("0.30"));
        assertThat(ChargeAssessor.completedOverduePeriods(t, LocalDate.of(2026, 11, 1))).isZero();
        assertThat(ChargeAssessor.completedOverduePeriods(t, LocalDate.of(2026, 11, 2))).isEqualTo(1);
        assertThat(ChargeAssessor.completedOverduePeriods(t, LocalDate.of(2026, 12, 1))).isEqualTo(2);
    }
}