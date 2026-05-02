package com.rinoimob.service;

import com.rinoimob.domain.dto.CreateTenantRoleRequest;
import com.rinoimob.domain.dto.TenantRoleResponse;
import com.rinoimob.domain.dto.UpdateTenantRoleRequest;
import com.rinoimob.domain.entity.RolePermission;
import com.rinoimob.domain.entity.TenantRole;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.Permission;
import com.rinoimob.domain.repository.RolePermissionRepository;
import com.rinoimob.domain.repository.TenantRoleRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.auth.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class TenantRoleService {
    private final TenantRoleRepository tenantRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;

    public TenantRoleService(TenantRoleRepository tenantRoleRepository,
                              RolePermissionRepository rolePermissionRepository,
                              UserRepository userRepository,
                              TokenService tokenService) {
        this.tenantRoleRepository = tenantRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    public List<TenantRoleResponse> listRoles(UUID tenantId) {
        return tenantRoleRepository.findByTenantId(tenantId).stream()
                .map(role -> toResponse(role, rolePermissionRepository.findPermissionValuesByRoleId(role.getId())))
                .toList();
    }

    public TenantRoleResponse getRole(UUID tenantId, UUID roleId) {
        TenantRole role = tenantRoleRepository.findByTenantIdAndId(tenantId, roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        List<String> permissions = rolePermissionRepository.findPermissionValuesByRoleId(roleId);
        return toResponse(role, permissions);
    }

    @Transactional
    public TenantRoleResponse createRole(UUID tenantId, CreateTenantRoleRequest request) {
        if (tenantRoleRepository.existsByTenantIdAndName(tenantId, request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role name already exists");
        }
        TenantRole role = new TenantRole();
        role.setTenantId(tenantId);
        role.setName(request.name());
        role.setDescription(request.description());
        role.setIsSystem(false);
        TenantRole saved = tenantRoleRepository.save(role);
        savePermissions(saved.getId(), request.permissions());
        return toResponse(saved, request.permissions() != null ? request.permissions() : List.of());
    }

    @Transactional
    public TenantRoleResponse updateRole(UUID tenantId, UUID roleId, UpdateTenantRoleRequest request) {
        TenantRole role = tenantRoleRepository.findByTenantIdAndId(tenantId, roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot modify system roles");
        }
        role.setName(request.name());
        role.setDescription(request.description());
        TenantRole saved = tenantRoleRepository.save(role);
        rolePermissionRepository.deleteByRoleId(roleId);
        savePermissions(roleId, request.permissions());

        // Role permissions changed — all users in this tenant with this role have stale JWT tokens.
        // Invalidate at tenant level since we can't efficiently target only users with this specific role.
        tokenService.invalidateAllTenantTokens(tenantId);

        return toResponse(saved, request.permissions() != null ? request.permissions() : List.of());
    }

    @Transactional
    public void deleteRole(UUID tenantId, UUID roleId) {
        TenantRole role = tenantRoleRepository.findByTenantIdAndId(tenantId, roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete system roles");
        }
        boolean hasUsers = userRepository.existsByTenantRoleId(roleId);
        if (hasUsers) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role is assigned to users");
        }
        rolePermissionRepository.deleteByRoleId(roleId);
        tenantRoleRepository.delete(role);
    }

    @Transactional
    public void seedDefaultRoles(UUID tenantId) {
        createSystemRole(tenantId, "Administrador", "Acesso total ao sistema", Permission.allValues());
        createSystemRole(tenantId, "Corretor", "Acesso a leads, tarefas, imóveis e WhatsApp",
                Arrays.asList("leads:read", "leads:write", "tasks:read", "tasks:write",
                        "properties:read", "properties:write", "whatsapp:read", "whatsapp:write"));
        createSystemRole(tenantId, "Visualizador", "Acesso de leitura apenas",
                Arrays.asList("leads:read", "properties:read", "tasks:read", "categories:read",
                        "whatsapp:read", "reports:read"));
    }

    public List<String> getPermissionsForUser(User user) {
        if (user.getSystemRole() != null) {
            return Permission.allValues();
        }
        if (user.getTenantRoleId() != null) {
            return rolePermissionRepository.findPermissionValuesByRoleId(user.getTenantRoleId());
        }
        return List.of();
    }

    private void createSystemRole(UUID tenantId, String name, String description, List<String> permissions) {
        if (!tenantRoleRepository.existsByTenantIdAndName(tenantId, name)) {
            TenantRole role = new TenantRole();
            role.setTenantId(tenantId);
            role.setName(name);
            role.setDescription(description);
            role.setIsSystem(true);
            TenantRole saved = tenantRoleRepository.save(role);
            savePermissions(saved.getId(), permissions);
        }
    }

    private void savePermissions(UUID roleId, List<String> permissions) {
        if (permissions == null) return;
        for (String perm : permissions) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermission(perm);
            rolePermissionRepository.save(rp);
        }
    }

    private TenantRoleResponse toResponse(TenantRole role, List<String> permissions) {
        return new TenantRoleResponse(
                role.getId(),
                role.getTenantId(),
                role.getName(),
                role.getDescription(),
                role.getIsSystem(),
                permissions
        );
    }
}
