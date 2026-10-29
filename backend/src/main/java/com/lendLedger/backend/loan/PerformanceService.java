package com.lendLedger.backend.loan;

import com.lendLedger.backend.common.ApiException;
import com.lendLedger.backend.common.TenantClock;
import com.lendLedger.backend.finance.BalanceComponent;
import com.lendLedger.backend.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
class PerformanceService {

    private static final int PAGE = 100;
    private static final long MAX_MONTHS = 120;

    private final LoanRepository loans;
    private final PerformanceRepository repo;
    private final LoanService loanService;
    private final TenantClock clock;

    PerformanceService(LoanRepository loans, PerformanceRepository repo, LoanService loanService, TenantClock clock) {
        this.loans = loans;
        this.repo = repo;
        this.loanService = loanService;
        this.clock = clock;
    }

    /** Whole calendar months from the month of {@code fromDate} to the month of {@code toDate}. */
    @Transactional(readOnly = true)
    PerformanceDtos.Performance performance(LocalDate fromDate, LocalDate toDate) {
        UUID tenantId = CurrentUser.tenantId();
        ZoneId zone = clock.zone();
        LocalDate today = LocalDate.now(zone);

        LocalDate from = fromDate.withDayOfMonth(1);
        LocalDate monthEnd = toDate.withDayOfMonth(toDate.lengthOfMonth());
        if (from.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_period", "The period cannot start in the future");
        }
        if (monthEnd.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_period", "The end month is before the start month");
        }
        if (ChronoUnit.MONTHS.between(from, monthEnd) >= MAX_MONTHS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "period_too_long", "Choose a period of 10 years or less");
        }
        LocalDate to = monthEnd.isAfter(today) ? today : monthEnd;

        PerformanceRepository.Lent lent = repo.lent(tenantId, from, to);
        PerformanceRepository.Received got = repo.received(tenantId, zone, from, to);
        PerformanceRepository.Completed done = repo.completed(tenantId, from, to);
        Set<UUID> rolledChildren = repo.activeRolledChildren(tenantId, from, to);

        // Overdue is derived from the loan terms, so open loans are classified with the same code the loan pages use.
        long onTrack = 0;
        long overdue = 0;
        long open = loans.count(tenantId, null, "ACTIVE");
        for (int offset = 0; offset < open; offset += PAGE) {
            List<LoanRow> page = loans.list(tenantId, null, "ACTIVE", PAGE, offset);
            Map<UUID, Map<BalanceComponent, BigDecimal>> allocated =
                    loans.allocatedNet(tenantId, page.stream().map(LoanRow::id).toList());
            for (LoanRow row : page) {
                if (row.issueDate().isBefore(from) || row.issueDate().isAfter(to) || rolledChildren.contains(row.id())) continue;
                LoanDtos.LoanView view = loanService.toView(row, allocated.getOrDefault(row.id(), Map.of()), today);
                if ("OVERDUE".equals(view.displayStatus())) overdue++;
                else onTrack++;
            }
        }

        return new PerformanceDtos.Performance(from, to, lent.loans(), lent.principal(),
                got.principal().add(got.interest()).add(got.penalty()), got.interest(),
                new PerformanceDtos.Outcomes(done.paidOff(), done.rolledOver(), onTrack, overdue));
    }
}