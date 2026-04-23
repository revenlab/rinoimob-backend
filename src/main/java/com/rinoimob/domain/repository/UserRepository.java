package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByTenantIdAndEmail(String tenantId, String email);

    List<User> findByTenantId(String tenantId);

    List<User> findByTenantIdAndActive(String tenantId, Boolean active);

}
