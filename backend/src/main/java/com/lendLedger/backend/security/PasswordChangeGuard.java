package com.lendLedger.backend.security;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * While a user must choose a new password, every API call except /me, sign-in/out and the account endpoints
 * is refused with 403 password_change_required.
 */
@Configuration
public class PasswordChangeGuard implements WebMvcConfigurer {

    private final JdbcClient jdbc;

    public PasswordChangeGuard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                String path = request.getRequestURI();
                if (!path.startsWith("/api/v1/") || path.equals("/api/v1/me")
                        || path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/account")) {
                    return true;
                }
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null || !(auth.getPrincipal() instanceof LendLedgerPrincipal user)) return true;

                boolean must = jdbc.sql("SELECT must_change_password FROM app_user WHERE tenant_id = :tenant AND id = :id")
                        .param("tenant", user.tenantId())
                        .param("id", user.userId())
                        .query(Boolean.class)
                        .optional()
                        .orElse(false);
                if (!must) return true;

                response.setStatus(403);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"password_change_required\",\"message\":\"Choose your own password to continue.\"}");
                return false;
            }
        }).addPathPatterns("/api/**");
    }
}