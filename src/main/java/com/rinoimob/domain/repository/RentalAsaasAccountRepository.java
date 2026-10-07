package com.rinoimob.domain.repository;
import com.rinoimob.domain.entity.RentalAsaasAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RentalAsaasAccountRepository extends JpaRepository<RentalAsaasAccount, UUID> {
    Optional<RentalAsaasAccount> findByProviderAccountId(String providerAccountId);
}
