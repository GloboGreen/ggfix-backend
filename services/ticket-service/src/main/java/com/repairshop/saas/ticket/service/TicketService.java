package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.dto.TicketRequest;
import com.repairshop.saas.ticket.dto.TicketResponse;
import com.repairshop.saas.ticket.entity.Technician;
import com.repairshop.saas.ticket.entity.Ticket;
import com.repairshop.saas.ticket.exception.ResourceNotFoundException;
import com.repairshop.saas.ticket.repository.TechnicianRepository;
import com.repairshop.saas.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TechnicianRepository technicianRepository;

    private static final String TRACKING_PREFIX = "CSPEN";

    @Transactional(readOnly = true)
    public TicketResponse getById(UUID shopId, UUID id) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
        return toResponse(t);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> listByShop(UUID shopId, String status, Pageable pageable) {
        Page<Ticket> page = status != null && !status.isBlank()
                ? ticketRepository.findByShopIdAndStatus(shopId, status, pageable)
                : ticketRepository.findByShopId(shopId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> listByAssignedTechnician(UUID technicianId, Pageable pageable) {
        return ticketRepository.findByAssignedTechnicianId(technicianId, pageable).map(this::toResponse);
    }

    /** For technician "my tickets": resolve user id to technician id then list assigned tickets. */
    @Transactional(readOnly = true)
    public Page<TicketResponse> listByAssignedUser(UUID userId, Pageable pageable) {
        return technicianRepository.findFirstByUserId(userId)
                .map(tech -> ticketRepository.findByAssignedTechnicianId(tech.getId(), pageable).map(this::toResponse))
                .orElse(new PageImpl<>(Collections.emptyList(), pageable, 0));
    }

    /**
     * Returns booking/ticket counts for the shop (for owner dashboard).
     * Keys: CREATED, IN_DIAGNOSIS, QUOTED, APPROVED, IN_REPAIR, READY, DELIVERED, CANCELLED, total, assignedCount.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getCountsByShop(UUID shopId) {
        Map<String, Long> counts = new HashMap<>();
        String[] statuses = { "CREATED", "IN_DIAGNOSIS", "QUOTED", "APPROVED", "IN_REPAIR", "READY", "DELIVERED", "CANCELLED" };
        for (String s : statuses) {
            counts.put(s, ticketRepository.countByShopIdAndStatus(shopId, s));
        }
        counts.put("total", ticketRepository.countByShopId(shopId));
        counts.put("assignedCount", ticketRepository.countByShopIdAndAssignedTechnicianIdNotNull(shopId));
        return counts;
    }

    @Transactional
    public TicketResponse create(UUID shopId, TicketRequest request) {
        String trackingId = generateTrackingId(shopId);
        Ticket ticket = Ticket.builder()
                .shopId(shopId)
                .customerId(request.getCustomerId())
                .brandId(request.getBrandId())
                .modelId(request.getModelId())
                .ramOptionId(request.getRamOptionId())
                .storageOptionId(request.getStorageOptionId())
                .color(request.getColor())
                .imei(request.getImei())
                .issueDescription(request.getIssueDescription())
                .estimatedPrice(request.getEstimatedPrice())
                .deviceDisplayName(request.getDeviceDisplayName())
                .deviceImageUrl(request.getDeviceImageUrl())
                .repairServicesSummary(request.getRepairServicesSummary())
                .priceItemsJson(request.getPriceItemsJson())
                .missingPartsJson(request.getMissingPartsJson())
                .devicePhotosJson(request.getDevicePhotosJson())
                .deviceSecurityType(request.getDeviceSecurityType())
                .deviceSecurityValue(request.getDeviceSecurityValue())
                .customerApproval(request.getCustomerApproval())
                .estimatedReadyAt(request.getEstimatedReadyAt())
                .estimatedDeliveryAt(request.getEstimatedDeliveryAt())
                .trackingId(trackingId)
                .status("CREATED")
                .build();
        ticket = ticketRepository.save(ticket);
        return toResponse(ticket);
    }

    @Transactional
    public TicketResponse update(UUID shopId, UUID id, TicketRequest request) {
        Ticket ticket = ticketRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
        ticket.setCustomerId(request.getCustomerId());
        ticket.setBrandId(request.getBrandId());
        ticket.setModelId(request.getModelId());
        ticket.setRamOptionId(request.getRamOptionId());
        ticket.setStorageOptionId(request.getStorageOptionId());
        ticket.setColor(request.getColor());
        ticket.setImei(request.getImei());
        ticket.setIssueDescription(request.getIssueDescription());
        ticket.setEstimatedPrice(request.getEstimatedPrice());
        ticket.setDeviceDisplayName(request.getDeviceDisplayName());
        ticket.setDeviceImageUrl(request.getDeviceImageUrl());
        ticket.setRepairServicesSummary(request.getRepairServicesSummary());
        ticket.setPriceItemsJson(request.getPriceItemsJson());
        ticket.setMissingPartsJson(request.getMissingPartsJson());
        ticket.setDevicePhotosJson(request.getDevicePhotosJson());
        ticket.setDeviceSecurityType(request.getDeviceSecurityType());
        ticket.setDeviceSecurityValue(request.getDeviceSecurityValue());
        ticket.setCustomerApproval(request.getCustomerApproval());
        ticket.setEstimatedReadyAt(request.getEstimatedReadyAt());
        ticket.setEstimatedDeliveryAt(request.getEstimatedDeliveryAt());
        ticket = ticketRepository.save(ticket);
        return toResponse(ticket);
    }

    @Transactional
    public void updateStatus(UUID shopId, UUID id, String status) {
        Ticket ticket = ticketRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
        ticket.setStatus(status);
        ticketRepository.save(ticket);
    }

    /**
     * Lightweight PATCH that currently supports assigning technicians and a few optional fields
     * from a generic map payload. This is primarily used by the mobile "Assign Technician" flow.
     */
    @Transactional
    public TicketResponse patch(UUID shopId, UUID id, Map<String, Object> body) {
        Ticket ticket = ticketRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));

        if (body.containsKey("assignedTechnicianId")) {
            Object raw = body.get("assignedTechnicianId");
            if (raw == null || String.valueOf(raw).isBlank()) {
                ticket.setAssignedTechnicianId(null);
            } else {
                UUID value = UUID.fromString(String.valueOf(raw));
                // Resolve to technician id: auth returns user id, ticket stores technicians.id
                Technician tech = technicianRepository.findByShopIdAndId(shopId, value)
                        .or(() -> technicianRepository.findByShopIdAndUserId(shopId, value))
                        .orElse(null);
                if (tech == null) {
                    // Auth may return user id with no technicians row; create one so assignment works
                    tech = technicianRepository.save(Technician.builder()
                            .shopId(shopId)
                            .userId(value)
                            .name("Technician")
                            .build());
                }
                ticket.setAssignedTechnicianId(tech.getId());
            }
        }

        if (body.containsKey("status")) {
            Object raw = body.get("status");
            ticket.setStatus(raw != null ? String.valueOf(raw) : ticket.getStatus());
        }

        ticket = ticketRepository.save(ticket);
        return toResponse(ticket);
    }

    private String generateTrackingId(UUID shopId) {
        String suffix = String.valueOf(System.currentTimeMillis() % 10000000);
        return TRACKING_PREFIX + suffix;
    }

    private TicketResponse toResponse(Ticket t) {
        return TicketResponse.builder()
                .id(t.getId())
                .shopId(t.getShopId())
                .customerId(t.getCustomerId())
                .assignedTechnicianId(t.getAssignedTechnicianId())
                .trackingId(t.getTrackingId())
                .brandId(t.getBrandId())
                .modelId(t.getModelId())
                .color(t.getColor())
                .status(t.getStatus())
                .estimatedPrice(t.getEstimatedPrice())
                .finalPrice(t.getFinalPrice())
                .issueDescription(t.getIssueDescription())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .deviceDisplayName(t.getDeviceDisplayName())
                .deviceImageUrl(t.getDeviceImageUrl())
                .repairServicesSummary(t.getRepairServicesSummary())
                .priceItemsJson(t.getPriceItemsJson())
                .missingPartsJson(t.getMissingPartsJson())
                .devicePhotosJson(t.getDevicePhotosJson())
                .deviceSecurityType(t.getDeviceSecurityType())
                .deviceSecurityValue(t.getDeviceSecurityValue())
                .customerApproval(t.getCustomerApproval())
                .estimatedReadyAt(t.getEstimatedReadyAt())
                .estimatedDeliveryAt(t.getEstimatedDeliveryAt())
                .build();
    }
}
