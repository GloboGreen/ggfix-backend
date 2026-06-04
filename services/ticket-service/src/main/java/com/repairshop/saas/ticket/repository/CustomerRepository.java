package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByShopIdOrderByCreatedAtDesc(UUID shopId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.shopId = :shopId AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) OR c.phone LIKE CONCAT('%', :q, '%')) ORDER BY c.createdAt DESC")
    List<Customer> findByShopIdAndSearch(@Param("shopId") UUID shopId, @Param("q") String q);

    Optional<Customer> findByShopIdAndPlatformUserId(UUID shopId, UUID platformUserId);

    @Query("SELECT c FROM Customer c WHERE c.shopId = :shopId AND REPLACE(REPLACE(REPLACE(c.phone, ' ', ''), '-', ''), '+', '') = :phone")
    List<Customer> findByShopIdAndNormalizedPhone(@Param("shopId") UUID shopId, @Param("phone") String phone);
}
