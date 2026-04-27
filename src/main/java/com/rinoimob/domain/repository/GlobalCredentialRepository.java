package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.GlobalCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GlobalCredentialRepository extends JpaRepository<GlobalCredential, String> {

    Optional<GlobalCredential> findByEmail(String email);
}
