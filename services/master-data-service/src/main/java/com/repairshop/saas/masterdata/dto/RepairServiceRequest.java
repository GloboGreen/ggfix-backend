package com.repairshop.saas.masterdata.dto;

import lombok.Data;

@Data
public class RepairServiceRequest {
    private String code;
    private String name;
    private String description;
}
