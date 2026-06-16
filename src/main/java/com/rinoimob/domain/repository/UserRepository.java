package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.rinoimob.domain.enums.SystemRole;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);

    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

    boolean existsByEmailAndTenantId(String email, UUID tenantId);

    List<User> findAllByEmail(String email);

    List<User> findByTenantId(UUID tenantId);

    List<User> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<User> findByTenantIdAndActive(UUID tenantId, Boolean active);

    List<User> findBySystemRoleInOrderByCreatedAtDesc(List<SystemRole> systemRoles);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantRoleId(UUID tenantRoleId);

    List<User> findByTenantRoleId(UUID tenantRoleId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndActive(UUID tenantId, Boolean active);

    @Query("SELECT COUNT(u) FROM User u WHERE u.systemRole IS NULL OR u.systemRole = com.rinoimob.domain.enums.SystemRole.TENANT_OWNER")
    long countNonInternalUsers();
}
