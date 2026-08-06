package com.umameats.chat.controller;

import com.umameats.chat.model.ChatMessage;
import com.umameats.chat.model.ChatPrincipal;
import com.umameats.chat.security.ChatIdentityResolver;
import com.umameats.chat.service.DeliveryChatService;
import com.umameats.chat.stream.ChatEventBus;
import com.umameats.model.Order;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer/driver chat for one delivery.
 *
 * <p>Nested under {@code /api/v1/orders} on purpose: that prefix already routes to
 * order-api at the load balancer, so this ships without an ALB change.
 */
@RestController
@RequestMapping("/api/v1/orders/{orderId}/chat")
public class DeliveryChatController {

    private final DeliveryChatService deliveryChatService;
    private final ChatIdentityResolver identityResolver;
    private final ChatEventBus chatEventBus;

    public DeliveryChatController(
            DeliveryChatService deliveryChatService,
            ChatIdentityResolver identityResolver,
            ChatEventBus chatEventBus) {
        this.deliveryChatService = deliveryChatService;
        this.identityResolver = identityResolver;
        this.chatEventBus = chatEventBus;
    }

    /**
     * Conversation metadata, so the client knows who it is talking to and whether
     * to show the composer at all.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getThread(
            @PathVariable String orderId,
            HttpServletRequest request) {
        ChatPrincipal principal = identityResolver.resolve(request);
        Order order = deliveryChatService.requireParticipant(orderId, principal);

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("open", deliveryChatService.isChatOpen(order));
        body.put("viewerRole", principal.role().name());
        body.put("counterpartRole", principal.isDriver() ? "CUSTOMER" : "DRIVER");
        body.put("counterpartName", counterpartName(order, principal));
        body.put("orderStatus", order.getStatus() != null ? order.getStatus().name() : null);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/messages")
    public ResponseEntity<Map<String, Object>> listMessages(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {
        ChatPrincipal principal = identityResolver.resolve(request);
        List<ChatMessage> messages = deliveryChatService.listMessages(orderId, principal, limit);
        return ResponseEntity.ok(Map.of("messages", messages, "total", messages.size()));
    }

    @PostMapping("/messages")
    public ResponseEntity<ChatMessage> postMessage(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        ChatPrincipal principal = identityResolver.resolve(request);
        ChatMessage message = deliveryChatService.postMessage(orderId, principal, body.get("body"));
        return ResponseEntity.ok(message);
    }

    /**
     * Live message stream. React Native cannot reliably set headers on an SSE
     * reconnect, so the token may also arrive as a query parameter.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String orderId,
            @RequestParam(required = false) String token,
            HttpServletRequest request) {
        ChatPrincipal principal = identityResolver.resolve(request, token);
        deliveryChatService.requireParticipant(orderId, principal);
        return chatEventBus.subscribe(ChatEventBus.deliveryTopic(orderId));
    }

    private String counterpartName(Order order, ChatPrincipal principal) {
        if (principal.isDriver()) {
            return order.getDeliveryAddress() != null ? order.getDeliveryAddress().getFullName() : null;
        }
        return order.getAssignedDriverName();
    }
}
