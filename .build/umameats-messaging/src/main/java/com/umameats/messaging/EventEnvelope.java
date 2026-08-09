package com.umameats.messaging;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned event envelope helpers shared across producers.
 */
public final class EventEnvelope {

    public static final String SCHEMA_VERSION = "1";

    private EventEnvelope() {
    }

    public static String newEventId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Ensures eventId / schemaVersion / timestamp exist on a mutable payload map.
     */
    public static Map<String, Object> enrich(Map<String, Object> payload, String eventType) {
        Map<String, Object> enriched = payload == null ? new HashMap<>() : new HashMap<>(payload);
        enriched.putIfAbsent("eventId", newEventId());
        enriched.putIfAbsent("schemaVersion", SCHEMA_VERSION);
        enriched.putIfAbsent("timestamp", System.currentTimeMillis());
        if (eventType != null && !enriched.containsKey("eventType")) {
            enriched.put("eventType", eventType);
        }
        return enriched;
    }

    public static String extractEventId(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object eventId = payload.get("eventId");
        return eventId != null ? String.valueOf(eventId) : null;
    }
}
