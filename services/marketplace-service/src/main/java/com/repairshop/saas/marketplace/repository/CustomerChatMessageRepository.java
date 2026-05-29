package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.CustomerChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerChatMessageRepository extends JpaRepository<CustomerChatMessage, UUID> {

    List<CustomerChatMessage> findByThreadIdOrderByCreatedAtAsc(UUID threadId);
}
