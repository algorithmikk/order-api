package com.umameats.config;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umameats.model.Order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outbox writer converts entities with this mapper before enqueueing an
 * event. A mapper without java.time support blows up on {@code orderDate},
 * which fails the enclosing status update after it has already been persisted
 * and stops the driver marketplace from ever hearing about the change.
 */
class JacksonConfigTest {

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    @Test
    void convertsOrderWithTimestampToMapForOutboxPayloads() {
        Order order = new Order();
        order.setOrderId("22a56ac2-81d5-4a9f-84a0-9a50dd1ddf6d");
        order.setOrderDate(LocalDateTime.of(2026, 8, 14, 0, 34, 54, 732061489));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.convertValue(order, Map.class);

        assertEquals("2026-08-14T00:34:54.732Z", payload.get("orderDate"));
    }

    @Test
    void serializesOrderTimestampAsUtcInstant() throws Exception {
        Order order = new Order();
        order.setOrderDate(LocalDateTime.of(2026, 8, 14, 0, 34, 54, 732061489));

        String json = objectMapper.writeValueAsString(order);

        assertTrue(json.contains("\"orderDate\":\"2026-08-14T00:34:54.732Z\""), json);
    }
}
