package com.lendLedger.backend.security;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated user as stored in the session. Carries the tenant ID, which is the ONLY place a
 * tenant ID may come from (ADR-05): never from a request parameter, header or body.
 */
public final class LendLedgerPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final UUID tenantId;
    private final String name;
    private final String email;
    private final Role role;
    private final boolean enabled;
    private final boolean locked;
    private String passwordHash;      // erased by Spring Security after authentication

    public LendLedgerPrincipal(UUID userId, UUID tenantId, String name, String email,
                               Role role, String passwordHash, boolean enabled, boolean locked) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.locked = locked;
    }

    public UUID userId() { return userId; }
    public UUID tenantId() { return tenantId; }
    public String name() { return name; }
    public String email() { return email; }
    public Role role() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        role.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
        return authorities;
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public boolean isAccountNonLocked() { return !locked; }

    @Override
    public void eraseCredentials() { this.passwordHash = null; }
}