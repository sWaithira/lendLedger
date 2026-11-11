package com.lendLedger.backend.security;

import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads users from app_user. A user whose tenant is not ACTIVE cannot sign in. */
@Service
public class LendLedgerUserDetailsService implements UserDetailsService {

    private final JdbcClient jdbc;
    private final LoginAttemptService loginAttempts;

    public LendLedgerUserDetailsService(JdbcClient jdbc, LoginAttemptService loginAttempts) {
        this.jdbc = jdbc;
        this.loginAttempts = loginAttempts;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        return jdbc.sql("""
                        SELECT u.id, u.tenant_id, u.name, u.email, u.password_hash, u.role, u.status
                        FROM app_user u
                        JOIN tenant t ON t.id = u.tenant_id
                        WHERE lower(u.email) = :email AND t.status = 'ACTIVE'
                        """)
                .param("email", normalized)
                .query((rs, rowNum) -> new LendLedgerPrincipal(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("email"),
                        Role.valueOf(rs.getString("role")),
                        rs.getString("password_hash"),
                        "ACTIVE".equals(rs.getString("status")),
                        loginAttempts.isLocked(normalized)))
                .optional()
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}