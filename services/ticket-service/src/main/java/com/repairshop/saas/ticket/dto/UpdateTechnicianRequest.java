package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Update technician (partial)")
public class UpdateTechnicianRequest {

    @Schema(description = "Display name")
    private String name;

    @Schema(description = "Email")
    private String email;

    @Schema(description = "Phone")
    private String phone;

    @Schema(description = "Role label")
    private String roleLabel;

    @Schema(description = "Available for assignment")
    private Boolean isAvailable;

    @Schema(description = "Salary amount")
    private String salaryAmount;

    @Schema(description = "Salary period e.g. Monthly, Weekly")
    private String salaryPeriod;

    @Schema(description = "ID verification type e.g. Aadhar, PAN")
    private String idVerificationType;

    @Schema(description = "ID number")
    private String idNumber;

    @Schema(description = "Date of birth")
    private LocalDate dateOfBirth;

    @Schema(description = "Date of join")
    private LocalDate dateOfJoin;

    @Schema(description = "Default check-in time")
    private LocalTime defaultCheckIn;

    @Schema(description = "Default check-out time")
    private LocalTime defaultCheckOut;

    @Schema(description = "Photo URL")
    private String photoUrl;
}
