package com.umameats.messaging.idempotency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for local/dev when DynamoDB is unavailable.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<String, Boolean> processed = new ConcurrentHashMap<>();

    @Override
    public boolean alreadyProcessed(String consumerGroup, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        return processed.containsKey(DynamoIdempotencyStore.pk(consumerGroup, eventId));
    }

    @Override
    public void markProcessed(String consumerGroup, String eventId, String topic, String orderId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        processed.put(DynamoIdempotencyStore.pk(consumerGroup, eventId), Boolean.TRUE);
    }
}
