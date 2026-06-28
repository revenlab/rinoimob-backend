package com.rinoimob.domain.enums;

import java.util.EnumSet;
import java.util.Set;

public enum SystemRole {
    TENANT_OWNER,
    TENANT_ADMIN,
    SUPPORT_MANAGER,
    SUPPORT_AGENT;

    private static final Set<SystemRole> INTERNAL_STAFF_ROLES =
            EnumSet.of(SUPPORT_MANAGER, SUPPORT_AGENT);

    public boolean isInternalStaff() {
        return INTERNAL_STAFF_ROLES.contains(this);
    }

    public boolean canManageSupport() {
        return this == SUPPORT_MANAGER;
    }
}
