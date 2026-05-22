package com.rinoimob.domain.enums;

public enum SupportPermission {
    TENANTS_READ("support:tenants:read"),
    TENANTS_WRITE("support:tenants:write"),
    TENANT_USERS_READ("support:tenant_users:read"),
    TENANT_USERS_WRITE("support:tenant_users:write"),
    OPERATORS_READ("support:operators:read"),
    OPERATORS_WRITE("support:operators:write"),
    AUDIT_READ("support:audit:read"),
    HEALTH_READ("support:health:read");

    private final String value;

    SupportPermission(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SupportPermission fromValue(String value) {
        for (SupportPermission permission : values()) {
            if (permission.value.equals(value)) {
                return permission;
            }
        }
        throw new IllegalArgumentException("Unknown support permission: " + value);
    }
}
