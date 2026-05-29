package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.SellOrderScreeningAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SellOrderScreeningAnswerRepository extends JpaRepository<SellOrderScreeningAnswer, UUID> {
    List<SellOrderScreeningAnswer> findBySellOrderId(UUID sellOrderId);
}
