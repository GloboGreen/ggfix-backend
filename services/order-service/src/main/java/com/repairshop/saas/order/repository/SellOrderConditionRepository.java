package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.SellOrderCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SellOrderConditionRepository extends JpaRepository<SellOrderCondition, UUID> {
    List<SellOrderCondition> findBySellOrderId(UUID sellOrderId);
}
