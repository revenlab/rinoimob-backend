package com.rinoimob.context;

import java.util.UUID;

public class TenantContext {

    private static final ThreadLocal<String> tenantId = new ThreadLocal<>();
    private static final ThreadLocal<UUID> userId = new ThreadLocal<>();

    public static void setTenantId(String id) {
        tenantId.set(id);
    }

    public static String getTenantId() {
        return tenantId.get();
    }

    public static void setUserId(UUID id) {
        userId.set(id);
    }

    public static UUID getUserId() {
        return userId.get();
    }

    public static void clear() {
        tenantId.remove();
        userId.remove();
    }

}
