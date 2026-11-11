package com.lendLedger.backend.security;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Checks the signed-in user's lender on every request.
 * Suspended lender: signed out on the very next request.
 * Subscription ended: the lender can still read and download, but every change is refused (read-only).
 * Deliberately NOT a Spring bean: a bean would also be registered as a plain servlet filter that runs before sign-in is
 * known, and OncePerRequestFilter would then skip the real run.
 */
final class TenantStatusFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> ALLOWED_WHEN_ENDED = Set.of("/api/v1/auth/logout", "/api/v1/account/password");

    /** endedOn is set only when the subscription has already ended. */
    private record State(boolean active, LocalDate endedOn) { }

    private final JdbcClient jdbc;

    TenantStatusFilter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LendLedgerPrincipal user) {
            State state = stateOf(user);
            if (!state.active()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
                write(response, HttpServletResponse.SC_UNAUTHORIZED, "account_suspended",
                        "This lender account is suspended. Contact us to get it reactivated.");
                return;
            }
            if (state.endedOn() != null && !SAFE_METHODS.contains(request.getMethod())
                    && !ALLOWED_WHEN_ENDED.contains(request.getRequestURI())) {
                write(response, HttpServletResponse.SC_FORBIDDEN, "subscription_ended",
                        "Your subscription ended on " + state.endedOn()
                                + ". You can still view and download your data. Contact us to renew.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private State stateOf(LendLedgerPrincipal user) {
        return jdbc.sql("""
                        SELECT t.status = 'ACTIVE' AS active,
                               (NOT t.is_platform AND t.subscription_expires_on IS NOT NULL
                                AND t.subscription_expires_on < current_date) AS ended,
                               t.subscription_expires_on AS expires_on
                        FROM tenant t WHERE t.id = :id
                        """)
                .param("id", user.tenantId())
                .query((rs, i) -> new State(rs.getBoolean("active"),
                        rs.getBoolean("ended") ? rs.getObject("expires_on", LocalDate.class) : null))
                .optional()
                .orElse(new State(false, null));
    }

    private static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}