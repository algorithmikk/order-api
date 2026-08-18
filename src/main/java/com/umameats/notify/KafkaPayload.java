package com.umameats.notify;

import java.util.HashMap;
import java.util.Map;

/**
 * Unwraps the nested { eventType, payload } envelopes used across UmaMeats Kafka topics.
 */
public final class KafkaPayload {

    private KafkaPayload() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> unwrap(Map<String, Object> root) {
        if (root == null) {
            return Map.of();
        }
        Object nested = root.get("payload");
        if (nested instanceof Map<?, ?> nestedMap) {
            Map<String, Object> payload = new HashMap<>((Map<String, Object>) nestedMap);
            payload.putIfAbsent("eventType", root.get("eventType"));
            payload.putIfAbsent("type", root.get("type"));
            payload.putIfAbsent("eventId", root.get("eventId"));
            payload.putIfAbsent("orderId", root.get("orderId"));
            payload.putIfAbsent("customerId", root.get("customerId"));
            return payload;
        }
        return root;
    }

    public static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }

    public static Integer intVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long longVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
