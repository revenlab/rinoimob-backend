package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findBySubdomain(String subdomain);

    Optional<Tenant> findByName(String name);

}
