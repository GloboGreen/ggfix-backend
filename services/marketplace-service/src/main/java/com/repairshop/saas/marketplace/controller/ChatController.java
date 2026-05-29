package com.repairshop.saas.marketplace.controller;

import com.repairshop.saas.marketplace.dto.ChatMessageResponse;
import com.repairshop.saas.marketplace.dto.ChatSendRequest;
import com.repairshop.saas.marketplace.dto.ChatThreadResponse;
import com.repairshop.saas.marketplace.entity.CustomerChatMessage;
import com.repairshop.saas.marketplace.entity.CustomerChatThread;
import com.repairshop.saas.marketplace.exception.ResourceNotFoundException;
import com.repairshop.saas.marketplace.repository.CustomerChatMessageRepository;
import com.repairshop.saas.marketplace.repository.CustomerChatThreadRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/customer/chats")
@RequiredArgsConstructor
public class ChatController {

    private final CustomerChatThreadRepository threadRepo;
    private final CustomerChatMessageRepository msgRepo;

    @GetMapping
    public ResponseEntity<List<ChatThreadResponse>> list(HttpServletRequest req) {
        UUID userId = callerId(req);
        return ResponseEntity.ok(threadRepo.findByCustomerUserIdOrderByLastMessageAtDesc(userId).stream()
                .map(this::toThreadResp).toList());
    }

    @PostMapping
    public ResponseEntity<ChatThreadResponse> open(HttpServletRequest req, @RequestParam("shopId") UUID shopId) {
        UUID userId = callerId(req);
        CustomerChatThread thread = threadRepo.findByCustomerUserIdAndShopId(userId, shopId)
                .orElseGet(() -> threadRepo.save(CustomerChatThread.builder()
                        .customerUserId(userId)
                        .shopId(shopId)
                        .subject("Conversation")
                        .build()));
        return ResponseEntity.ok(toThreadResp(thread));
    }

    @GetMapping("/{threadId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> messages(HttpServletRequest req, @PathVariable UUID threadId) {
        UUID userId = callerId(req);
        ensureOwner(userId, threadId);
        return ResponseEntity.ok(msgRepo.findByThreadIdOrderByCreatedAtAsc(threadId).stream()
                .map(this::toMsgResp).toList());
    }

    @PostMapping("/{threadId}/messages")
    public ResponseEntity<ChatMessageResponse> send(HttpServletRequest req, @PathVariable UUID threadId, @RequestBody ChatSendRequest body) {
        UUID userId = callerId(req);
        CustomerChatThread t = threadRepo.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + threadId));
        if (!t.getCustomerUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your thread");
        }
        CustomerChatMessage m = msgRepo.save(CustomerChatMessage.builder()
                .threadId(threadId)
                .sender("CUSTOMER")
                .body(body.getBody())
                .attachmentUrl(body.getAttachmentUrl())
                .build());
        t.setLastMessageAt(Instant.now());
        t.setLastMessagePreview(body.getBody() != null && body.getBody().length() > 200 ? body.getBody().substring(0, 200) : body.getBody());
        threadRepo.save(t);
        return ResponseEntity.ok(toMsgResp(m));
    }

    private void ensureOwner(UUID userId, UUID threadId) {
        CustomerChatThread t = threadRepo.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + threadId));
        if (!t.getCustomerUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your thread");
        }
    }

    private ChatThreadResponse toThreadResp(CustomerChatThread t) {
        return ChatThreadResponse.builder()
                .id(t.getId())
                .shopId(t.getShopId())
                .subject(t.getSubject())
                .lastMessagePreview(t.getLastMessagePreview())
                .lastMessageAt(t.getLastMessageAt())
                .build();
    }

    private ChatMessageResponse toMsgResp(CustomerChatMessage m) {
        return ChatMessageResponse.builder()
                .id(m.getId())
                .threadId(m.getThreadId())
                .sender(m.getSender())
                .body(m.getBody())
                .attachmentUrl(m.getAttachmentUrl())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private UUID callerId(HttpServletRequest req) {
        Object u = req.getAttribute("userId");
        if (u == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing userId");
        return UUID.fromString(u.toString());
    }
}
