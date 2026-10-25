package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.lendLedger.backend.finance.BalanceComponent;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL for loans. EVERY query filters on tenant_id (ADR-05). */
@Repository
class LoanRepository {

    record RuleInfo(UUID ruleVersionId, int versionNumber, String interestMethod, BigDecimal ratePerPeriod,
                    int graceDays, BigDecimal penaltyRatePerPeriod, BigDecimal penaltyCap,
                    List<BalanceComponent> allocationOrder, RoundingMode roundingMode,
                    String productName, String currency, BigDecimal minPrincipal, BigDecimal maxPrincipal,
                    int maxTermPeriods) { }

    private record AllocationSum(UUID loanId, BalanceComponent component, BigDecimal total) { }

    private static final String LOAN_SELECT = """
            SELECT l.id, l.loan_number, l.borrower_id, b.full_name AS borrower_name, l.product_id, p.name AS product_name,
                   l.rule_version_id, r.version_number, l.principal, l.currency, l.issue_date, l.term_periods,
                   l.due_date, l.status, l.settled_on, r.rate_per_period, r.grace_days,
                   r.penalty_rate_per_period, r.penalty_cap, r.allocation_order, r.rounding_mode
            FROM loan l
            JOIN borrower b ON b.tenant_id = l.tenant_id AND b.id = l.borrower_id
            JOIN loan_product p ON p.tenant_id = l.tenant_id AND p.id = l.product_id
            JOIN loan_rule_version r ON r.tenant_id = l.tenant_id AND r.id = l.rule_version_id
            """;

    private final JdbcClient jdbc;

    LoanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ reads

    Optional<LoanRow> findById(UUID tenantId, UUID id) {
        return jdbc.sql(LOAN_SELECT + " WHERE l.tenant_id = :tenant AND l.id = :id")
                .param("tenant", tenantId)
                .param("id", id)
                .query(LoanRepository::mapLoan)
                .optional();
    }

    List<LoanRow> list(UUID tenantId, UUID borrowerId, String status, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        String where = filter(tenantId, borrowerId, status, params);
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.sql(LOAN_SELECT + where + " ORDER BY l.created_at DESC, l.id LIMIT :limit OFFSET :offset")
                .params(params)
                .query(LoanRepository::mapLoan)
                .list();
    }

    long count(UUID tenantId, UUID borrowerId, String status) {
        Map<String, Object> params = new HashMap<>();
        String where = filter(tenantId, borrowerId, status, params);
        return jdbc.sql("SELECT count(*) FROM loan l" + where)
                .params(params)
                .query(Long.class)
                .single();
    }

    /** All loans of the tenant, optionally one stored status (null = every status), earliest due date first. */
    List<LoanRow> listByStatus(UUID tenantId, String status) {
        Map<String, Object> params = new HashMap<>();
        String where = filter(tenantId, null, status, params);
        return jdbc.sql(LOAN_SELECT + where + " ORDER BY l.due_date, l.id")
                .params(params)
                .query(LoanRepository::mapLoan)
                .list();
    }

    /** Net of reversals: original allocations are positive, their mirror rows negative. */
    Map<UUID, Map<BalanceComponent, BigDecimal>> allocatedNet(UUID tenantId, Collection<UUID> loanIds) {
        Map<UUID, Map<BalanceComponent, BigDecimal>> result = new HashMap<>();
        if (loanIds.isEmpty()) return result;
        List<AllocationSum> sums = jdbc.sql("""
                        SELECT loan_id, component, SUM(amount) AS total
                        FROM payment_allocation
                        WHERE tenant_id = :tenant AND loan_id IN (:ids)
                        GROUP BY loan_id, component
                        """)
                .param("tenant", tenantId)
                .param("ids", loanIds)
                .query((rs, n) -> new AllocationSum(rs.getObject("loan_id", UUID.class),
                        BalanceComponent.valueOf(rs.getString("component")), rs.getBigDecimal("total")))
                .list();
        for (AllocationSum s : sums) {
            result.computeIfAbsent(s.loanId(), k -> new EnumMap<>(BalanceComponent.class)).put(s.component(), s.total());
        }
        return result;
    }

    /** Net allocations for every loan of the tenant in one query (used by reports). */
    Map<UUID, Map<BalanceComponent, BigDecimal>> allocatedNetAll(UUID tenantId) {
        Map<UUID, Map<BalanceComponent, BigDecimal>> result = new HashMap<>();
        List<AllocationSum> sums = jdbc.sql("""
                        SELECT loan_id, component, SUM(amount) AS total
                        FROM payment_allocation
                        WHERE tenant_id = :tenant
                        GROUP BY loan_id, component
                        """)
                .param("tenant", tenantId)
                .query((rs, n) -> new AllocationSum(rs.getObject("loan_id", UUID.class),
                        BalanceComponent.valueOf(rs.getString("component")), rs.getBigDecimal("total")))
                .list();
        for (AllocationSum s : sums) {
            result.computeIfAbsent(s.loanId(), k -> new EnumMap<>(BalanceComponent.class)).put(s.component(), s.total());
        }
        return result;
    }

    Optional<String> borrowerStatus(UUID tenantId, UUID borrowerId) {
        return jdbc.sql("SELECT status FROM borrower WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId)
                .param("id", borrowerId)
                .query(String.class)
                .optional();
    }

    Optional<RuleInfo> findEffectiveRule(UUID tenantId, UUID productId, LocalDate on) {
        return jdbc.sql("""
                        SELECT r.id, r.version_number, r.interest_method, r.rate_per_period, r.grace_days,
                               r.penalty_rate_per_period, r.penalty_cap, r.allocation_order, r.rounding_mode,
                               p.name, p.currency, p.min_principal, p.max_principal, p.max_term_periods
                        FROM loan_product p
                        JOIN loan_rule_version r ON r.tenant_id = p.tenant_id AND r.product_id = p.id
                        WHERE p.tenant_id = :tenant AND p.id = :product AND p.active
                          AND r.status = 'APPROVED' AND r.effective_from <= :on
                          AND (r.effective_to IS NULL OR r.effective_to > :on)
                        """)
                .param("tenant", tenantId)
                .param("product", productId)
                .param("on", on)
                .query((rs, n) -> new RuleInfo(
                        rs.getObject("id", UUID.class), rs.getInt("version_number"), rs.getString("interest_method"),
                        rs.getBigDecimal("rate_per_period"), rs.getInt("grace_days"),
                        rs.getBigDecimal("penalty_rate_per_period"), rs.getBigDecimal("penalty_cap"),
                        readOrder(rs), RoundingMode.valueOf(rs.getString("rounding_mode")),
                        rs.getString("name"), rs.getString("currency"),
                        rs.getBigDecimal("min_principal"), rs.getBigDecimal("max_principal"),
                        rs.getInt("max_term_periods")))
                .optional();
    }

    List<LoanDtos.ProductView> listProducts(UUID tenantId, LocalDate on) {
        return jdbc.sql("""
                        SELECT p.id, p.name, p.currency, p.min_principal, p.max_principal, p.max_term_periods,
                               r.id AS rule_id, r.version_number, r.rate_per_period, r.grace_days,
                               r.penalty_rate_per_period
                        FROM loan_product p
                        JOIN loan_rule_version r ON r.tenant_id = p.tenant_id AND r.product_id = p.id
                         AND r.status = 'APPROVED' AND r.effective_from <= :on
                         AND (r.effective_to IS NULL OR r.effective_to > :on)
                        WHERE p.tenant_id = :tenant AND p.active
                        ORDER BY p.name
                        """)
                .param("tenant", tenantId)
                .param("on", on)
                .query((rs, n) -> new LoanDtos.ProductView(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("currency"),
                        rs.getBigDecimal("min_principal"), rs.getBigDecimal("max_principal"),
                        rs.getObject("rule_id", UUID.class), rs.getInt("version_number"),
                        rs.getBigDecimal("rate_per_period"), rs.getInt("grace_days"),
                        rs.getBigDecimal("penalty_rate_per_period"), rs.getInt("max_term_periods")))
                .list();
    }

    // ------------------------------------------------------------------ writes

    /** Locks the loan row until the transaction ends, so payments on one loan are processed one at a time (ADR-03). */
    Optional<LoanRow> lockById(UUID tenantId, UUID id) {
        return jdbc.sql(LOAN_SELECT + " WHERE l.tenant_id = :tenant AND l.id = :id FOR UPDATE OF l")
                .param("tenant", tenantId)
                .param("id", id)
                .query(LoanRepository::mapLoan)
                .optional();
    }

    void markSettled(UUID tenantId, UUID loanId, LocalDate on) {
        jdbc.sql("""
                        UPDATE loan SET status = 'COMPLETED', settled_on = :on
                        WHERE tenant_id = :tenant AND id = :id AND status = 'ACTIVE'
                        """)
                .param("on", on)
                .param("tenant", tenantId)
                .param("id", loanId)
                .update();
    }

    void reopen(UUID tenantId, UUID loanId) {
        jdbc.sql("""
                        UPDATE loan SET status = 'ACTIVE', settled_on = NULL
                        WHERE tenant_id = :tenant AND id = :id AND status = 'COMPLETED'
                        """)
                .param("tenant", tenantId)
                .param("id", loanId)
                .update();
    }

    /** The next short number for this lender. The upsert locks the counter row, so numbers are never doubled. */
    int nextLoanNumber(UUID tenantId) {
        return jdbc.sql("""
                        INSERT INTO tenant_counter (tenant_id, name, next_value) VALUES (:tenant, 'loan', 2)
                        ON CONFLICT (tenant_id, name) DO UPDATE SET next_value = tenant_counter.next_value + 1
                        RETURNING next_value - 1
                        """)
                .param("tenant", tenantId)
                .query(Integer.class)
                .single();
    }

    void insertLoan(UUID id, int loanNumber, UUID tenantId, UUID borrowerId, UUID productId, UUID ruleVersionId,
                    BigDecimal principal, String currency, LocalDate issueDate, int termPeriods,
                    LocalDate dueDate, String snapshotJson, UUID createdBy) {
        jdbc.sql("""
                        INSERT INTO loan (id, loan_number, tenant_id, borrower_id, product_id, rule_version_id, principal,
                                          currency, issue_date, term_periods, due_date, status, terms_snapshot, created_by)
                        VALUES (:id, :number, :tenant, :borrower, :product, :rule, :principal, :currency,
                                :issue, :term, :due, 'ACTIVE', CAST(:snapshot AS jsonb), :user)
                        """)
                .param("id", id)
                .param("number", loanNumber)
                .param("tenant", tenantId)
                .param("borrower", borrowerId)
                .param("product", productId)
                .param("rule", ruleVersionId)
                .param("principal", principal)
                .param("currency", currency)
                .param("issue", issueDate)
                .param("term", termPeriods)
                .param("due", dueDate)
                .param("snapshot", snapshotJson)
                .param("user", createdBy)
                .update();
    }

    void insertCharge(UUID tenantId, UUID loanId, String type, String kind, int period,
                      LocalDate effectiveDate, BigDecimal amount, UUID ruleVersionId) {
        jdbc.sql("""
                        INSERT INTO charge_event (tenant_id, loan_id, charge_type, period_kind, period_number,
                                                  effective_date, amount, rule_version_id)
                        VALUES (:tenant, :loan, :type, :kind, :period, :effective, :amount, :rule)
                        ON CONFLICT (loan_id, charge_type, period_kind, period_number) DO NOTHING
                        """)
                .param("tenant", tenantId)
                .param("loan", loanId)
                .param("type", type)
                .param("kind", kind)
                .param("period", period)
                .param("effective", effectiveDate)
                .param("amount", amount)
                .param("rule", ruleVersionId)
                .update();
    }

    // ------------------------------------------------------------------ helpers

    private static String filter(UUID tenantId, UUID borrowerId, String status, Map<String, Object> params) {
        StringBuilder where = new StringBuilder(" WHERE l.tenant_id = :tenant");
        params.put("tenant", tenantId);
        if (borrowerId != null) {
            where.append(" AND l.borrower_id = :borrower");
            params.put("borrower", borrowerId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND l.status = :status");
            params.put("status", status);
        }
        return where.toString();
    }

    private static LoanRow mapLoan(ResultSet rs, int rowNum) throws SQLException {
        return new LoanRow(
                rs.getObject("id", UUID.class), rs.getInt("loan_number"), rs.getObject("borrower_id", UUID.class),
                rs.getString("borrower_name"), rs.getObject("product_id", UUID.class), rs.getString("product_name"),
                rs.getObject("rule_version_id", UUID.class), rs.getInt("version_number"),
                rs.getBigDecimal("principal"), rs.getString("currency"),
                rs.getObject("issue_date", LocalDate.class), rs.getInt("term_periods"),
                rs.getObject("due_date", LocalDate.class), rs.getString("status"),
                rs.getObject("settled_on", LocalDate.class),
                rs.getBigDecimal("rate_per_period"), rs.getInt("grace_days"),
                rs.getBigDecimal("penalty_rate_per_period"), rs.getBigDecimal("penalty_cap"),
                readOrder(rs), RoundingMode.valueOf(rs.getString("rounding_mode")));
    }

    private static List<BalanceComponent> readOrder(ResultSet rs) throws SQLException {
        Array array = rs.getArray("allocation_order");
        try {
            String[] names = (String[]) array.getArray();
            return Arrays.stream(names).map(BalanceComponent::valueOf).toList();
        } finally {
            array.free();
        }
    }
}