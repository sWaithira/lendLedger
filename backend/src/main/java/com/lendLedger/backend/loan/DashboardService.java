package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.lendLedger.backend.audit.AuditService;
import com.lendLedger.backend.common.ApiException;
import com.lendLedger.backend.common.PageResponse;
import com.lendLedger.backend.common.TenantClock;
import com.lendLedger.backend.finance.BalanceComponent;
import com.lendLedger.backend.finance.ChargeAssessor;
import com.lendLedger.backend.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_PERIOD_DAYS = 1830;
    private static final long MAX_DAILY_DAYS = 366;

    private record ActiveLoan(LoanRow row, LoanDtos.LoanView view) { }

    private record Period(LocalDate from, LocalDate to) { }

    private final LoanRepository loans;
    private final DashboardRepository dashboard;
    private final LoanService loanService;
    private final AuditService audit;
    private final TenantClock clock;

    public DashboardService(LoanRepository loans, DashboardRepository dashboard, LoanService loanService,
                            AuditService audit, TenantClock clock) {
        this.loans = loans;
        this.dashboard = dashboard;
        this.loanService = loanService;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ dashboard

    @Transactional(readOnly = true)
    public DashboardDtos.Summary summary(LocalDate from, LocalDate to) {
        UUID tenantId = CurrentUser.tenantId();
        ZoneId zone = clock.zone();
        LocalDate today = LocalDate.now(zone);
        Period period = period(from, to, today);

        long overdue = 0;
        long dueSoon = 0;
        BigDecimal principal = BigDecimal.ZERO, interest = BigDecimal.ZERO, penalty = BigDecimal.ZERO;
        BigDecimal oPrincipal = BigDecimal.ZERO, oInterest = BigDecimal.ZERO, oPenalty = BigDecimal.ZERO;

        List<ActiveLoan> active = activeLoans(tenantId, today);
        for (ActiveLoan a : active) {
            LoanDtos.BalanceView b = a.view().outstanding();
            principal = principal.add(b.principal());
            interest = interest.add(b.interest());
            penalty = penalty.add(b.penalty());
            if ("OVERDUE".equals(a.view().displayStatus())) {
                overdue++;
                oPrincipal = oPrincipal.add(b.principal());
                oInterest = oInterest.add(b.interest());
                oPenalty = oPenalty.add(b.penalty());
            } else if ("DUE_SOON".equals(a.view().displayStatus())) {
                dueSoon++;
            }
        }

        Map<String, Long> counts = dashboard.loanCountsByStatus(tenantId);
        DashboardDtos.Portfolio portfolio = new DashboardDtos.Portfolio(active.size(), overdue, dueSoon,
                counts.getOrDefault("COMPLETED", 0L),
                balance(principal, interest, penalty), balance(oPrincipal, oInterest, oPenalty));

        BigDecimal cTotal = BigDecimal.ZERO, cPrincipal = BigDecimal.ZERO, cInterest = BigDecimal.ZERO, cPenalty = BigDecimal.ZERO;
        for (DashboardRepository.CollectionRow r : dashboard.collections(tenantId, zone, period.from(), period.to(), "month")) {
            cTotal = cTotal.add(r.total());
            cPrincipal = cPrincipal.add(r.principal());
            cInterest = cInterest.add(r.interest());
            cPenalty = cPenalty.add(r.penalty());
        }

        DashboardRepository.Disbursement d = dashboard.disbursed(tenantId, period.from(), period.to());
        return new DashboardDtos.Summary(today, period.from(), period.to(), portfolio,
                new DashboardDtos.CollectionTotals(cTotal, cPrincipal, cInterest, cPenalty),
                new DashboardDtos.Disbursements(d.count(), d.total()));
    }

    @Transactional(readOnly = true)
    public List<DashboardDtos.CollectionPoint> collections(LocalDate from, LocalDate to, String granularity) {
        UUID tenantId = CurrentUser.tenantId();
        ZoneId zone = clock.zone();
        Period period = period(from, to, LocalDate.now(zone));

        boolean monthly = granularity == null || !"day".equals(granularity.trim().toLowerCase(Locale.ROOT));
        if (!monthly && ChronoUnit.DAYS.between(period.from(), period.to()) > MAX_DAILY_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "period_too_long", "Daily figures are limited to 366 days");
        }

        Map<LocalDate, DashboardRepository.CollectionRow> byStart = dashboard
                .collections(tenantId, zone, period.from(), period.to(), monthly ? "month" : "day").stream()
                .collect(Collectors.toMap(DashboardRepository.CollectionRow::periodStart, Function.identity()));

        List<DashboardDtos.CollectionPoint> points = new ArrayList<>();
        LocalDate cursor = monthly ? period.from().withDayOfMonth(1) : period.from();
        while (!cursor.isAfter(period.to())) {
            DashboardRepository.CollectionRow r = byStart.get(cursor);
            points.add(r == null
                    ? new DashboardDtos.CollectionPoint(cursor, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                    : new DashboardDtos.CollectionPoint(cursor, r.total(), r.principal(), r.interest(), r.penalty()));
            cursor = monthly ? cursor.plusMonths(1) : cursor.plusDays(1);
        }
        return points;
    }

    @Transactional(readOnly = true)
    public PageResponse<DashboardDtos.OverdueItem> overdue(int minDays, int page, int size) {
        UUID tenantId = CurrentUser.tenantId();
        List<DashboardDtos.OverdueItem> all = overdueItems(tenantId, LocalDate.now(clock.zone()), minDays);

        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 0);
        int fromIndex = Math.min(pageNumber * pageSize, all.size());
        int toIndex = Math.min(fromIndex + pageSize, all.size());
        int totalPages = (int) Math.ceil(all.size() / (double) pageSize);
        return new PageResponse<>(all.subList(fromIndex, toIndex), pageNumber, pageSize, all.size(), totalPages);
    }

    // ------------------------------------------------------------------ exports (audited: they contain personal data)

    @Transactional
    public DashboardDtos.CsvFile loansCsv() {
        UUID tenantId = CurrentUser.tenantId();
        LocalDate today = LocalDate.now(clock.zone());
        List<LoanRow> rows = loans.listByStatus(tenantId, null);
        Map<UUID, Map<BalanceComponent, BigDecimal>> allocated = loans.allocatedNetAll(tenantId);
        Map<UUID, DashboardRepository.Contact> contacts = dashboard.borrowerContacts(tenantId);

        CsvWriter csv = new CsvWriter().row("loan_number", "borrower", "phone", "product", "principal", "currency",
                "issue_date", "due_date", "term_months", "status", "display_status", "days_overdue",
                "outstanding_principal", "outstanding_interest", "outstanding_penalty", "outstanding_total", "as_of");
        for (LoanRow row : rows) {
            LoanDtos.LoanView v = loanService.toView(row, allocated.getOrDefault(row.id(), Map.of()), today);
            csv.row(LoanDtos.reference(row.loanNumber()), row.borrowerName(), phone(contacts, row.borrowerId()),
                    row.productName(), row.principal(), row.currency(), row.issueDate(), row.dueDate(),
                    row.termPeriods(), row.status(), v.displayStatus(), v.daysOverdue(), v.outstanding().principal(),
                    v.outstanding().interest(), v.outstanding().penalty(), v.outstanding().total(), v.asOf());
        }
        audit.record("REPORT_EXPORTED", "REPORT", null, null, Map.of("report", "loans", "rows", String.valueOf(rows.size())));
        return new DashboardDtos.CsvFile("loans-" + today + ".csv", csv.build());
    }

    @Transactional
    public DashboardDtos.CsvFile repaymentsCsv(LocalDate from, LocalDate to) {
        UUID tenantId = CurrentUser.tenantId();
        LocalDate today = LocalDate.now(clock.zone());
        Period period = period(from, to, today);
        List<DashboardRepository.PaymentRow> rows = dashboard.payments(tenantId, period.from(), period.to());

        CsvWriter csv = new CsvWriter().row("payment_date", "recorded_at", "loan_number", "borrower", "amount", "method",
                "reference", "principal", "interest", "penalty", "status", "reversed_at", "reversal_reason");
        for (DashboardRepository.PaymentRow r : rows) {
            csv.row(r.paymentDate(), r.recordedAt(), LoanDtos.reference(r.loanNumber()), r.borrowerName(), r.amount(),
                    r.method(), r.reference(), r.principal(), r.interest(), r.penalty(),
                    r.reversedAt() == null ? "POSTED" : "REVERSED", r.reversedAt(), r.reversalReason());
        }
        audit.record("REPORT_EXPORTED", "REPORT", null, null, Map.of("report", "repayments",
                "from", period.from().toString(), "to", period.to().toString(), "rows", String.valueOf(rows.size())));
        return new DashboardDtos.CsvFile("repayments-" + period.from() + "-to-" + period.to() + ".csv", csv.build());
    }

    @Transactional
    public DashboardDtos.CsvFile overdueCsv(int minDays) {
        UUID tenantId = CurrentUser.tenantId();
        LocalDate today = LocalDate.now(clock.zone());
        List<DashboardDtos.OverdueItem> items = overdueItems(tenantId, today, minDays);

        CsvWriter csv = new CsvWriter().row("loan_number", "borrower", "phone", "principal", "due_date", "days_overdue",
                "overdue_periods", "outstanding_principal", "outstanding_interest", "outstanding_penalty",
                "outstanding_total", "last_payment_date");
        for (DashboardDtos.OverdueItem i : items) {
            csv.row(LoanDtos.reference(i.loanNumber()), i.borrowerName(), i.borrowerPhone(), i.principal(), i.dueDate(),
                    i.daysOverdue(), i.overduePeriods(), i.outstanding().principal(), i.outstanding().interest(),
                    i.outstanding().penalty(), i.outstanding().total(), i.lastPaymentDate());
        }
        audit.record("REPORT_EXPORTED", "REPORT", null, null, Map.of("report", "overdue", "rows", String.valueOf(items.size())));
        return new DashboardDtos.CsvFile("overdue-" + today + ".csv", csv.build());
    }

    // ------------------------------------------------------------------ helpers

    private List<ActiveLoan> activeLoans(UUID tenantId, LocalDate today) {
        List<LoanRow> rows = loans.listByStatus(tenantId, "ACTIVE");
        Map<UUID, Map<BalanceComponent, BigDecimal>> allocated = loans.allocatedNetAll(tenantId);
        List<ActiveLoan> result = new ArrayList<>();
        for (LoanRow row : rows) {
            result.add(new ActiveLoan(row, loanService.toView(row, allocated.getOrDefault(row.id(), Map.of()), today)));
        }
        return result;
    }

    private List<DashboardDtos.OverdueItem> overdueItems(UUID tenantId, LocalDate today, int minDays) {
        Map<UUID, DashboardRepository.Contact> contacts = dashboard.borrowerContacts(tenantId);
        Map<UUID, LocalDate> lastPayments = dashboard.lastPaymentDates(tenantId);
        int threshold = Math.max(minDays, 1);

        return activeLoans(tenantId, today).stream()
                .filter(a -> "OVERDUE".equals(a.view().displayStatus()) && a.view().daysOverdue() >= threshold)
                .sorted(Comparator.comparingLong((ActiveLoan a) -> a.view().daysOverdue()).reversed()
                        .thenComparing(a -> a.view().outstanding().total(), Comparator.<BigDecimal>reverseOrder())
                        .thenComparing(a -> a.row().id()))
                .map(a -> new DashboardDtos.OverdueItem(a.row().id(), a.row().loanNumber(), a.row().borrowerId(),
                        a.row().borrowerName(), phone(contacts, a.row().borrowerId()), email(contacts, a.row().borrowerId()),
                        a.row().principal(), a.row().issueDate(), a.row().dueDate(), a.view().daysOverdue(),
                        ChargeAssessor.completedOverduePeriods(a.row().terms(), today), a.view().outstanding(),
                        lastPayments.get(a.row().id())))
                .toList();
    }

    private static String phone(Map<UUID, DashboardRepository.Contact> contacts, UUID borrowerId) {
        DashboardRepository.Contact c = contacts.get(borrowerId);
        return c == null ? null : c.phone();
    }

    private static String email(Map<UUID, DashboardRepository.Contact> contacts, UUID borrowerId) {
        DashboardRepository.Contact c = contacts.get(borrowerId);
        return c == null ? null : c.email();
    }

    private static Period period(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (start.isAfter(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_period", "The start date must not be after the end date");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_PERIOD_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "period_too_long", "Choose a period of 5 years or less");
        }
        return new Period(start, end);
    }

    private static LoanDtos.BalanceView balance(BigDecimal principal, BigDecimal interest, BigDecimal penalty) {
        return new LoanDtos.BalanceView(principal, interest, penalty, principal.add(interest).add(penalty));
    }
}