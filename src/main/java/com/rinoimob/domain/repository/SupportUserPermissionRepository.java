package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.SupportUserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupportUserPermissionRepository extends JpaRepository<SupportUserPermission, Long> {

    List<SupportUserPermission> findByUserId(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM SupportUserPermission p WHERE p.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Query("SELECT permission.permission FROM SupportUserPermission permission WHERE permission.userId = :userId")
    List<String> findPermissionValuesByUserId(@Param("userId") UUID userId);
}
