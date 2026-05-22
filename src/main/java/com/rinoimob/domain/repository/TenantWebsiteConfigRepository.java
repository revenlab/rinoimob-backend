package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.TenantWebsiteConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantWebsiteConfigRepository extends JpaRepository<TenantWebsiteConfig, UUID> {
}
