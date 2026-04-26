package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);

    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

    boolean existsByEmailAndTenantId(String email, UUID tenantId);

    List<User> findAllByEmail(String email);

    List<User> findByTenantId(UUID tenantId);

    List<User> findByTenantIdAndActive(UUID tenantId, Boolean active);

}
