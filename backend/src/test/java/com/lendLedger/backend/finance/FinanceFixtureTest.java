package com.lendLedger.backend.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * The spec section 3.7 fixtures, as permanent tests. These encode the DEMO DEFAULTS (30% flat monthly on
 * original principal, interest before principal), not signed-off lender policy. When the lender supplies
 * real examples, add them here; never delete a fixture to make a build pass.
 */
class FinanceFixtureTest {

    private static final LocalDate ISSUE = LocalDate.of(2026, 1, 10);   // due 2026-02-10 for a 1-month term

    private static LoanTerms base() {
        return LoanTerms.flat(new BigDecimal("10000.00"), ISSUE, 1, new BigDecimal("0.30"));
    }

    private static Outstanding owedOn(LoanTerms terms, LocalDate asOf) {
        return LedgerBalance.compute(terms, ChargeAssessor.assess(terms, asOf), Map.of());
    }

    private static String show(List<Allocation> allocations) {
        return allocations.stream()
                .map(a -> a.component() + "=" + a.amount().toPlainString())
                .collect(Collectors.joining(","));
    }

    private static BigDecimal chargesOf(List<AssessedCharge> charges, ChargeType type) {
        return charges.stream().filter(c -> c.type() == type)
                .map(AssessedCharge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---- fixtures from section 3.7 -------------------------------------------------------------

    @Test
    void payingTheFullAmountClearsTheLoan() {
        LoanTerms terms = base();
        Outstanding due = owedOn(terms, ISSUE);
        assertThat(due.total()).isEqualByComparingTo("13000.00");

        List<Allocation> allocations = AllocationEngine.allocate(new BigDecimal("13000.00"), due, terms.allocationOrder());

        assertThat(show(allocations)).isEqualTo("INTEREST=3000.00,PRINCIPAL=10000.00");
        assertThat(due.minus(allocations).isSettled()).isEqualTo(true);
    }

    @Test
    void payingOnlyTheInterestLeavesPrincipalUntouched() {
        LoanTerms terms = base();
        Outstanding due = owedOn(terms, ISSUE);

        List<Allocation> allocations = AllocationEngine.allocate(new BigDecimal("3000.00"), due, terms.allocationOrder());

        assertThat(show(allocations)).isEqualTo("INTEREST=3000.00");
        Outstanding after = due.minus(allocations);
        assertThat(after.interest()).isEqualByComparingTo("0.00");
        assertThat(after.principal()).isEqualByComparingTo("10000.00");
    }

    @Test
    void aPartialPaymentClearsInterestThenReducesPrincipal() {
        LoanTerms terms = base();
        Outstanding due = owedOn(terms, ISSUE);

        List<Allocation> allocations = AllocationEngine.allocate(new BigDecimal("5000.00"), due, terms.allocationOrder());

        assertThat(show(allocations)).isEqualTo("INTEREST=3000.00,PRINCIPAL=2000.00");
        assertThat(due.minus(allocations).principal()).isEqualByComparingTo("8000.00");
    }

    @Test
    void oneOverduePeriodAddsAnotherInterestCharge() {
        LoanTerms terms = base();
        LocalDate oneMonthPastDue = terms.dueDate().plusMonths(1);

        List<AssessedCharge> charges = ChargeAssessor.assess(terms, oneMonthPastDue);

        assertThat(chargesOf(charges, ChargeType.INTEREST)).isEqualByComparingTo("6000.00");
        assertThat(owedOn(terms, oneMonthPastDue).total()).isEqualByComparingTo("16000.00");
    }

    @Test
    void threeOverduePeriodsAddNineThousand() {
        LoanTerms terms = base();
        LocalDate threeMonthsPastDue = terms.dueDate().plusMonths(3);

        List<AssessedCharge> charges = ChargeAssessor.assess(terms, threeMonthsPastDue);

        assertThat(chargesOf(charges, ChargeType.INTEREST)).isEqualByComparingTo("12000.00");   // 3,000 term + 9,000
    }

    @Test
    void fivePercentPenaltyForThreePeriodsIsFifteenHundred() {
        LoanTerms terms = base().withPenalty(new BigDecimal("0.05"), null);

        List<AssessedCharge> charges = ChargeAssessor.assess(terms, terms.dueDate().plusMonths(3));

        assertThat(chargesOf(charges, ChargeType.PENALTY)).isEqualByComparingTo("1500.00");
    }

    // ---- timing, dates, rounding ---------------------------------------------------------------

    @Test
    void aPeriodOnlyCountsOnceItIsComplete() {
        LoanTerms terms = base();
        LocalDate lastDayOfSecondPeriod = terms.dueDate().plusMonths(3).minusDays(1);

        assertThat(ChargeAssessor.completedOverduePeriods(terms, lastDayOfSecondPeriod)).isEqualTo(2);
        assertThat(ChargeAssessor.completedOverduePeriods(terms, terms.dueDate().plusMonths(3))).isEqualTo(3);
    }

    @Test
    void graceDaysDelayTheOverdueClock() {
        LoanTerms terms = base().withGraceDays(5);

        assertThat(ChargeAssessor.isOverdue(terms, terms.dueDate().plusDays(5))).isEqualTo(false);
        assertThat(ChargeAssessor.isOverdue(terms, terms.dueDate().plusDays(6))).isEqualTo(true);
        assertThat(ChargeAssessor.completedOverduePeriods(terms, terms.dueDate().plusMonths(1))).isEqualTo(0);
        assertThat(ChargeAssessor.completedOverduePeriods(terms, terms.dueDate().plusDays(5).plusMonths(1))).isEqualTo(1);
    }

    @Test
    void monthEndDueDatesClampInsteadOfOverflowing() {
        LoanTerms nonLeap = LoanTerms.flat(new BigDecimal("1000.00"), LocalDate.of(2026, 1, 31), 1, new BigDecimal("0.10"));
        LoanTerms leap = LoanTerms.flat(new BigDecimal("1000.00"), LocalDate.of(2028, 1, 31), 1, new BigDecimal("0.10"));

        assertThat(nonLeap.dueDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(leap.dueDate()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void overdueMonthsAreCountedFromTheAnchorSoTheyDoNotDrift() {
        // anchor 28 Feb: plusMonths(2) is 28 Apr. Stepping month by month from 31 Jan would give 31 Mar / 30 Apr.
        LoanTerms terms = LoanTerms.flat(new BigDecimal("1000.00"), LocalDate.of(2026, 1, 31), 1, new BigDecimal("0.10"));

        assertThat(ChargeAssessor.completedOverduePeriods(terms, LocalDate.of(2026, 3, 28))).isEqualTo(1);
        assertThat(ChargeAssessor.completedOverduePeriods(terms, LocalDate.of(2026, 4, 28))).isEqualTo(2);
    }

    @Test
    void chargesAreRoundedHalfUpToTwoDecimals() {
        LoanTerms terms = LoanTerms.flat(new BigDecimal("1000.50"), ISSUE, 1, new BigDecimal("0.125"));   // 125.0625

        assertThat(terms.periodInterest()).isEqualByComparingTo("125.06");
        LoanTerms half = LoanTerms.flat(new BigDecimal("1000.04"), ISSUE, 1, new BigDecimal("0.125"));    // 125.005
        assertThat(half.periodInterest()).isEqualByComparingTo("125.01");
    }

    @Test
    void nothingIsChargedBeforeTheIssueDate() {
        assertThat(ChargeAssessor.assess(base(), ISSUE.minusDays(1)).size()).isEqualTo(0);
    }

    // ---- allocation rules ----------------------------------------------------------------------

    @Test
    void penaltiesArePaidBeforeInterestByDefault() {
        LoanTerms terms = base().withPenalty(new BigDecimal("0.05"), null);
        Outstanding due = owedOn(terms, terms.dueDate().plusMonths(1));    // penalty 500, interest 6000, principal 10000

        List<Allocation> allocations = AllocationEngine.allocate(new BigDecimal("600.00"), due, terms.allocationOrder());

        assertThat(show(allocations)).isEqualTo("PENALTY=500.00,INTEREST=100.00");
    }

    @Test
    void theAllocationOrderComesFromTheTermsNotFromCode() {
        LoanTerms terms = base().withAllocationOrder(
                List.of(BalanceComponent.PRINCIPAL, BalanceComponent.INTEREST, BalanceComponent.PENALTY));
        Outstanding due = owedOn(terms, ISSUE);

        List<Allocation> allocations = AllocationEngine.allocate(new BigDecimal("4000.00"), due, terms.allocationOrder());

        assertThat(show(allocations)).isEqualTo("PRINCIPAL=4000.00");
    }

    @Test
    void penaltyCapLimitsTheTotalPenalty() {
        LoanTerms terms = base().withPenalty(new BigDecimal("0.05"), new BigDecimal("1000.00"));

        List<AssessedCharge> charges = ChargeAssessor.assess(terms, terms.dueDate().plusMonths(3));

        assertThat(chargesOf(charges, ChargeType.PENALTY)).isEqualByComparingTo("1000.00");   // 500 + 500 + 0
    }

    @Test
    void overpaymentIsRejected() {
        LoanTerms terms = base();
        Outstanding due = owedOn(terms, ISSUE);

        assertThatThrownBy(() -> AllocationEngine.allocate(new BigDecimal("13000.01"), due, terms.allocationOrder()))
                .isInstanceOf(OverpaymentException.class);
    }

    @Test
    void allocationsAlwaysSumToThePayment() {
        LoanTerms terms = base().withPenalty(new BigDecimal("0.05"), null);
        Outstanding due = owedOn(terms, terms.dueDate().plusMonths(2));

        for (String amount : List.of("0.01", "499.99", "500.00", "3500.01", "9999.99", "20000.00")) {
            BigDecimal payment = new BigDecimal(amount);
            BigDecimal sum = AllocationEngine.allocate(payment, due, terms.allocationOrder()).stream()
                    .map(Allocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo(payment);
        }
    }

    // ---- ledger invariants ---------------------------------------------------------------------

    @Test
    void reversingAPaymentRestoresTheExactPreviousBalance() {
        LoanTerms terms = base();
        List<AssessedCharge> charges = ChargeAssessor.assess(terms, ISSUE);
        Outstanding before = LedgerBalance.compute(terms, charges, Map.of());

        List<Allocation> allocations = AllocationEngine.allocate(new BigDecimal("5000.00"), before, terms.allocationOrder());
        Map<BalanceComponent, BigDecimal> paid = new EnumMap<>(BalanceComponent.class);
        allocations.forEach(a -> paid.merge(a.component(), a.amount(), BigDecimal::add));
        Outstanding afterPayment = LedgerBalance.compute(terms, charges, paid);
        assertThat(afterPayment.total()).isEqualByComparingTo("8000.00");

        Map<BalanceComponent, BigDecimal> net = new EnumMap<>(BalanceComponent.class);
        allocations.forEach(a -> net.merge(a.component(), a.amount().add(a.amount().negate()), BigDecimal::add));   // original + mirror row
        Outstanding afterReversal = LedgerBalance.compute(terms, charges, net);

        assertThat(afterReversal.penalty()).isEqualByComparingTo(before.penalty());
        assertThat(afterReversal.interest()).isEqualByComparingTo(before.interest());
        assertThat(afterReversal.principal()).isEqualByComparingTo(before.principal());
    }

    @Test
    void assessmentIsDeterministic() {
        LoanTerms terms = base().withPenalty(new BigDecimal("0.05"), null);
        LocalDate asOf = terms.dueDate().plusMonths(4);

        assertThat(ChargeAssessor.assess(terms, asOf)).isEqualTo(ChargeAssessor.assess(terms, asOf));
    }

    @Test
    void settlingStopsAccrual() {
        LoanTerms terms = base();
        LocalDate settledOn = terms.dueDate().plusDays(3);
        LocalDate reportDate = terms.dueDate().plusMonths(6);

        LocalDate effective = settledOn.isBefore(reportDate) ? settledOn : reportDate;   // callers pass min(asOf, settledOn)

        assertThat(chargesOf(ChargeAssessor.assess(terms, effective), ChargeType.INTEREST)).isEqualByComparingTo("3000.00");
    }

    @Test
    void roundingModeIsRespected() {
        LoanTerms down = new LoanTerms(new BigDecimal("1000.04"), ISSUE, 1, new BigDecimal("0.125"), 0,
                BigDecimal.ZERO, null, LoanTerms.DEFAULT_ORDER, RoundingMode.DOWN);

        assertThat(down.periodInterest()).isEqualByComparingTo("125.00");
    }
}