package com.repairshop.saas.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerOrderResponse {
    private UUID id;
    private String orderNumber;
    private UUID shopId;
    private String orderType;
    private UUID referenceId;
    private String status;
    private BigDecimal totalAmount;
    private Map<String, Object> payload;
    private Instant createdAt;
    private Instant updatedAt;
}
