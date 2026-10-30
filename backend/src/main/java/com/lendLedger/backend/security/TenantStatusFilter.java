package com.lendLedger.backend.security;

import java.io.IOException;

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
 * Signs a user out on their very next request when their lender is suspended or its subscription has ended, instead of
 * waiting for the session to expire. Deliberately NOT a Spring bean: a bean would also be registered as a plain servlet
 * filter that runs before sign-in is known, and OncePerRequestFilter would then skip the real run.
 */
final class TenantStatusFilter extends OncePerRequestFilter {

    private final JdbcClient jdbc;

    TenantStatusFilter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LendLedgerPrincipal user && !stillAllowed(user)) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"account_suspended\",\"message\":\"This lender account is suspended or its subscription has ended.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean stillAllowed(LendLedgerPrincipal user) {
        return jdbc.sql("""
                        SELECT (t.status = 'ACTIVE'
                                AND (t.is_platform OR t.subscription_expires_on IS NULL OR t.subscription_expires_on >= current_date))
                        FROM tenant t WHERE t.id = :id
                        """)
                .param("id", user.tenantId())
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }
}