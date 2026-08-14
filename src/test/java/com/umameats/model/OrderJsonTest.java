package com.umameats.model;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the wire format of {@code orderDate}. The value is stored naive but is
 * always UTC, so it has to reach clients with an explicit {@code Z} and at
 * millisecond precision — otherwise JavaScript renders every order shifted by
 * the viewer's UTC offset, or fails to parse it at all.
 */
class OrderJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void orderDateSerializesAsUtcInstantWithMillisecondPrecision() {
        Order order = new Order();
        order.setOrderDate(LocalDateTime.of(2026, 8, 14, 0, 34, 54, 732061489));

        String json = mapper.writeValueAsString(order);

        assertTrue(json.contains("\"orderDate\":\"2026-08-14T00:34:54.732Z\""), json);
    }

    @Test
    void orderDatePadsSubSecondPrecisionToThreeDigits() {
        Order order = new Order();
        order.setOrderDate(LocalDateTime.of(2026, 8, 14, 0, 34, 54, 0));

        String json = mapper.writeValueAsString(order);

        assertTrue(json.contains("\"orderDate\":\"2026-08-14T00:34:54.000Z\""), json);
    }
}
