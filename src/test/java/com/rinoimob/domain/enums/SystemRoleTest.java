package com.rinoimob.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemRoleTest {

    @Test
    void tenantAdminIsNotInternalStaff() {
        assertThat(SystemRole.TENANT_ADMIN.isInternalStaff()).isFalse();
        assertThat(SystemRole.TENANT_ADMIN.canManageSupport()).isFalse();
    }

    @Test
    void supportRolesAreInternalStaff() {
        assertThat(SystemRole.SUPPORT_MANAGER.isInternalStaff()).isTrue();
        assertThat(SystemRole.SUPPORT_MANAGER.canManageSupport()).isTrue();
        assertThat(SystemRole.SUPPORT_AGENT.isInternalStaff()).isTrue();
        assertThat(SystemRole.SUPPORT_AGENT.canManageSupport()).isFalse();
    }
}
