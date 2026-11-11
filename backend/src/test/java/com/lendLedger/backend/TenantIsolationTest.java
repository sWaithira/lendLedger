package com.lendLedger.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.lendLedger.backend.borrower.BorrowerDtos;
import com.lendLedger.backend.borrower.BorrowerService;
import com.lendLedger.backend.lender.LenderService;
import com.lendLedger.backend.loan.DashboardService;
import com.lendLedger.backend.loan.LoanDtos;
import com.lendLedger.backend.loan.LoanService;
import com.lendLedger.backend.loan.RepaymentService;
import com.lendLedger.backend.security.LendLedgerPrincipal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves that one lender cannot see another lender's data. Two lenders are created inside a transaction that is rolled
 * back afterwards; lender A gets a borrower and an overdue loan, then every read is tried as lender B.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class TenantIsolationTest {

    private static final String EMAIL_A = "owner.a@isolation.test";
    private static final String EMAIL_B = "owner.b@isolation.test";
    private static final String PASSWORD = "Isolation#2026pass";
    private static final String BORROWER_NAME = "Isolation Borrower A";

    @Autowired LenderService lenders;
    @Autowired BorrowerService borrowers;
    @Autowired LoanService loanService;
    @Autowired RepaymentService repayments;
    @Autowired DashboardService dashboard;
    @Autowired UserDetailsService users;
    @PersistenceContext EntityManager em;

    private UUID borrowerA;
    private UUID loanA;

    @BeforeEach
    void twoLendersAndOneLoan() {
        lenders.createForOperator("Isolation Lender A", "Owner A", EMAIL_A, PASSWORD);
        lenders.createForOperator("Isolation Lender B", "Owner B", EMAIL_B, PASSWORD);

        signInAs(EMAIL_A);
        UUID product = loanService.products().get(0).id();
        borrowerA = borrowers.create(new BorrowerDtos.CreateRequest(BORROWER_NAME, "0711000111", null, "99990001", null, null)).id();
        em.flush();   // the loan code reads borrowers with plain SQL, so the new row must reach the database first
        loanA = loanService.create(new LoanDtos.CreateRequest(borrowerA, product, new BigDecimal("5000.00"),
                LocalDate.now().minusMonths(3), 1)).id();
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lenderACanSeeItsOwnData() {
        signInAs(EMAIL_A);
        assertThat(borrowers.get(borrowerA).fullName()).isEqualTo(BORROWER_NAME);
        assertThat(loanService.get(loanA).id()).isEqualTo(loanA);
        assertThat(dashboard.overdue(1, 0, 100).items()).anyMatch(o -> o.loanId().equals(loanA));
        assertThat(dashboard.loansCsv().content()).contains(BORROWER_NAME);
    }

    @Test
    void lenderBCannotReadLenderAsBorrowerOrLoanById() {
        signInAs(EMAIL_B);
        assertThatThrownBy(() -> borrowers.get(borrowerA)).hasMessageContaining("not found");
        assertThatThrownBy(() -> loanService.get(loanA)).hasMessageContaining("not found");
        assertThatThrownBy(() -> repayments.list(loanA)).hasMessageContaining("not found");
        assertThatThrownBy(() -> repayments.statement(loanA)).hasMessageContaining("not found");
    }

    @Test
    void lenderBCannotReverseALenderAPayment() {
        signInAs(EMAIL_B);
        assertThatThrownBy(() -> repayments.reverse(UUID.randomUUID(), "trying anyway")).hasMessageContaining("not found");
    }

    @Test
    void listsReportsAndExportsOfLenderBContainNothingOfLenderA() {
        signInAs(EMAIL_B);
        assertThat(borrowers.list(null, null, 0, 100).items()).noneMatch(b -> b.id().equals(borrowerA));
        assertThat(dashboard.overdue(1, 0, 100).items()).noneMatch(o -> o.loanId().equals(loanA));
        assertThat(dashboard.loansCsv().content()).doesNotContain(BORROWER_NAME);
    }

    private void signInAs(String email) {
        LendLedgerPrincipal principal = (LendLedgerPrincipal) users.loadUserByUsername(email);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}