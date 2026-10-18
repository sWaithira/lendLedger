package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DashboardDtos {

    private DashboardDtos() { }

    public record Portfolio(long activeLoans, long overdueLoans, long dueSoonLoans, long completedLoans,
                            LoanDtos.BalanceView outstanding, LoanDtos.BalanceView overdueOutstanding) { }

    public record CollectionTotals(BigDecimal total, BigDecimal principal, BigDecimal interest, BigDecimal penalty) { }

    public record Disbursements(long loans, BigDecimal principal) { }

    /** asOf is today in the lender's timezone; collections and disbursements cover from..to inclusive. */
    public record Summary(LocalDate asOf, LocalDate from, LocalDate to, Portfolio portfolio,
                          CollectionTotals collections, Disbursements disbursements) { }

    public record CollectionPoint(LocalDate periodStart, BigDecimal total, BigDecimal principal,
                                  BigDecimal interest, BigDecimal penalty) { }

    public record OverdueItem(java.util.UUID loanId, java.util.UUID borrowerId, String borrowerName,
                              String borrowerPhone, BigDecimal principal, LocalDate issueDate, LocalDate dueDate,
                              long daysOverdue, int overduePeriods, LoanDtos.BalanceView outstanding,
                              LocalDate lastPaymentDate) { }

    public record CsvFile(String filename, String content) { }
}