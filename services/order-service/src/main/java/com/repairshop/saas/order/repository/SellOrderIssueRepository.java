package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.SellOrderIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SellOrderIssueRepository extends JpaRepository<SellOrderIssue, UUID> {
    List<SellOrderIssue> findBySellOrderId(UUID sellOrderId);
}
