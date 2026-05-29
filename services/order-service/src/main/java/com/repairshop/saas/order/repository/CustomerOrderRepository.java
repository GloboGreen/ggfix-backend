package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    List<CustomerOrder> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);
    List<CustomerOrder> findByCustomerUserIdAndOrderTypeOrderByCreatedAtDesc(UUID customerUserId, String orderType);
    Optional<CustomerOrder> findByOrderNumber(String orderNumber);
}
