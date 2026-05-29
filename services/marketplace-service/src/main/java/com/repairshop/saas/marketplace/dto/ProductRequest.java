package com.repairshop.saas.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {
    private UUID shopId;
    private UUID brandId;
    private UUID modelId;

    @NotBlank
    private String title;

    private String description;

    private String type; // SELL | BUY

    private BigDecimal price;

    private String status;

    private String conditionLabel;
    private String color;
    private String storageLabel;
    private String network;
    private String imageUrl;
    private List<String> extraImageUrls;
}
