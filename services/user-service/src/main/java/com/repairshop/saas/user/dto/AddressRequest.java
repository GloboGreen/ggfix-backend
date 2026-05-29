package com.repairshop.saas.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressRequest {

    private String label;
    private String fullName;
    private String mobile;
    private String pincode;
    private String locality;
    private String addressLine;
    private String city;
    private String state;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean isDefault;
}
