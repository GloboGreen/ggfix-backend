package com.repairshop.saas.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Event in the customer-facing service timeline. Returned by both the owner
 * BookingTimelineScreen (GET /tickets/{id}/events) and the customer
 * RepairOrderHistoryScreen (events list embedded in repair-bookings GET).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketEventResponse {
    private UUID id;
    private String status;
    private String note;
    private String actor;
    private Instant createdAt;
}
