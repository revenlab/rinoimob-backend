package com.rinoimob.domain.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "role_permissions")
public class RolePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(nullable = false)
    private String permission;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRoleId() { return roleId; }
    public void setRoleId(UUID roleId) { this.roleId = roleId; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
}
