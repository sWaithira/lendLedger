package com.lendLedger.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void lenderRolesNeverGetThePlatformPermission() {
        assertThat(Role.OWNER.permissions()).doesNotContain(Permission.PLATFORM_MANAGE);
        assertThat(Role.LOAN_OFFICER.permissions()).doesNotContain(Permission.PLATFORM_MANAGE);
    }

    @Test
    void ownerStillHasEveryLenderPermission() {
        EnumSet<Permission> lender = EnumSet.allOf(Permission.class);
        lender.remove(Permission.PLATFORM_MANAGE);
        assertThat(Role.OWNER.permissions()).containsExactlyInAnyOrderElementsOf(lender);
    }

    @Test
    void platformAdminHasOnlyThePlatformPermission() {
        assertThat(Role.PLATFORM_ADMIN.permissions()).containsExactly(Permission.PLATFORM_MANAGE);
    }
}