package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ModelRequest {
    private UUID brandId;
    private String name;
    private String imageUrl;
    private String imageBase64;
    private String category;
}
