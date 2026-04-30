package com.rinoimob.domain.enums;

import java.util.Arrays;
import java.util.List;

public enum Permission {
    LEADS_READ("leads:read"),
    LEADS_WRITE("leads:write"),
    PROPERTIES_READ("properties:read"),
    PROPERTIES_WRITE("properties:write"),
    TASKS_READ("tasks:read"),
    TASKS_WRITE("tasks:write"),
    TASK_TYPES_READ("task_types:read"),
    TASK_TYPES_WRITE("task_types:write"),
    CATEGORIES_READ("categories:read"),
    CATEGORIES_WRITE("categories:write"),
    WHATSAPP_READ("whatsapp:read"),
    WHATSAPP_WRITE("whatsapp:write"),
    USERS_READ("users:read"),
    USERS_WRITE("users:write"),
    ROLES_READ("roles:read"),
    ROLES_WRITE("roles:write"),
    SETTINGS_MANAGE("settings:manage"),
    REPORTS_READ("reports:read");

    private final String value;

    Permission(String value) { this.value = value; }

    public String getValue() { return value; }

    public static List<String> allValues() {
        return Arrays.stream(values()).map(Permission::getValue).toList();
    }
}
