package com.umameats.messaging.idempotency;

/**
 * Deduplicates consumer processing by consumer-group + eventId.
 */
public interface IdempotencyStore {

    /**
     * @return true if this event has already been processed successfully
     */
    boolean alreadyProcessed(String consumerGroup, String eventId);

    /**
     * Marks an event as successfully processed. Safe to call multiple times.
     */
    void markProcessed(String consumerGroup, String eventId, String topic, String orderId);
}
