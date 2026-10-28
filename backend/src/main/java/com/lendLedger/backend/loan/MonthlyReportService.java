package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lendLedger.backend.common.TenantClock;
import com.lendLedger.backend.security.CurrentUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MonthlyReportService {

    private static final int MAX_MONTHS = 24;

    private final MonthlyReportRepository repo;
    private final TenantClock clock;

    MonthlyReportService(MonthlyReportRepository repo, TenantClock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** The last {@code months} calendar months including this one, with empty months filled in as zeros. */
    @Transactional(readOnly = true)
    List<MonthlyReportDtos.MonthPoint> monthly(int months) {
        UUID tenantId = CurrentUser.tenantId();
        ZoneId zone = clock.zone();
        int count = Math.min(Math.max(months, 1), MAX_MONTHS);
        LocalDate today = LocalDate.now(zone);
        LocalDate first = today.withDayOfMonth(1).minusMonths(count - 1L);

        Map<LocalDate, MonthlyReportRepository.Lent> lent = new HashMap<>();
        for (MonthlyReportRepository.Lent l : repo.lent(tenantId, first, today)) lent.put(l.monthStart(), l);
        Map<LocalDate, MonthlyReportRepository.Collected> collected = new HashMap<>();
        for (MonthlyReportRepository.Collected c : repo.collected(tenantId, zone, first, today)) collected.put(c.monthStart(), c);

        List<MonthlyReportDtos.MonthPoint> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate month = first.plusMonths(i);
            MonthlyReportRepository.Lent l = lent.get(month);
            MonthlyReportRepository.Collected c = collected.get(month);
            BigDecimal principal = c == null ? BigDecimal.ZERO : c.principal();
            BigDecimal interest = c == null ? BigDecimal.ZERO : c.interest();
            BigDecimal penalty = c == null ? BigDecimal.ZERO : c.penalty();
            out.add(new MonthlyReportDtos.MonthPoint(month,
                    l == null ? 0 : l.loans(),
                    l == null ? BigDecimal.ZERO : l.principal(),
                    principal.add(interest).add(penalty), principal, interest, penalty));
        }
        return out;
    }
}