package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    List<RolePermission> findByRoleId(UUID roleId);

    @Transactional
    void deleteByRoleId(UUID roleId);

    @Query("SELECT rp.permission FROM RolePermission rp WHERE rp.roleId = :roleId")
    List<String> findPermissionValuesByRoleId(UUID roleId);
}
