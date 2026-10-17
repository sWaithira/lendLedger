package com.lendLedger.backend.common;

import java.time.LocalDate;
import java.time.ZoneId;

import com.lendLedger.backend.security.CurrentUser;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** "Today" in the lender's own timezone (ADR-04), so due dates and overdue status do not flip at the wrong hour. */
@Component
public class TenantClock {

    private final JdbcClient jdbc;

    public TenantClock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public LocalDate today() {
        String zone = jdbc.sql("SELECT timezone FROM tenant WHERE id = :id")
                .param("id", CurrentUser.tenantId())
                .query(String.class)
                .single();
        return LocalDate.now(ZoneId.of(zone));
    }
}