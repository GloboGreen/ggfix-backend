package com.repairshop.saas.marketplace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatThreadResponse {
    private UUID id;
    private UUID shopId;
    private String subject;
    private String lastMessagePreview;
    private Instant lastMessageAt;
}
