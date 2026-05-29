package com.repairshop.saas.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSendRequest {

    @NotBlank
    private String body;

    private String attachmentUrl;
}
