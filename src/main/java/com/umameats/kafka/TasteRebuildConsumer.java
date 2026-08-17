package com.umameats.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umameats.model.Order;
import com.umameats.notify.KafkaPayload;
import com.umameats.repository.OrderRepository;
import com.umameats.service.TasteProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Rebuilds umameats-user-taste when a driver (or any other service) marks
 * an order DELIVERED. Order-api status patches already call TasteProfileService
 * directly; this covers the driver-api path via umameats.delivery.events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TasteRebuildConsumer {

    public static final String CONSUMER_GROUP = "order-api-taste-rebuild-group";

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final TasteProfileService tasteProfileService;

    @KafkaListener(
            topics = {"umameats.delivery.events", "umameats.order.status"},
            groupId = CONSUMER_GROUP,
            properties = "auto.offset.reset:latest")
    public void consume(
            @Payload String eventJson,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key
    ) {
        Map<String, Object> root;
        try {
            root = objectMapper.readValue(eventJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return;
        }
        Map<String, Object> payload = KafkaPayload.unwrap(root);
        String status = first(
                KafkaPayload.stringVal(payload.get("status")),
                KafkaPayload.stringVal(payload.get("newStatus")),
                KafkaPayload.stringVal(payload.get("reason")));
        if (status == null || !status.toUpperCase().contains("DELIVERED")) {
            return;
        }
        String customerId = KafkaPayload.stringVal(payload.get("customerId"));
        if (customerId == null || customerId.isBlank()) {
            String orderId = first(KafkaPayload.stringVal(payload.get("orderId")), key);
            if (orderId != null) {
                customerId = orderRepository.findById(orderId, "").map(Order::getCustomerId).orElse(null);
            }
        }
        if (customerId != null && !customerId.isBlank()) {
            tasteProfileService.rebuildAsync(customerId);
        }
    }

    private static String first(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
