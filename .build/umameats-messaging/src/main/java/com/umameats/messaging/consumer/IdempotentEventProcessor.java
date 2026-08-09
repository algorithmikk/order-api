package com.umameats.messaging.consumer;

import com.umameats.messaging.EventEnvelope;
import com.umameats.messaging.TraceContext;
import com.umameats.messaging.idempotency.IdempotencyStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Runs a Kafka handler once per eventId for a consumer group.
 */
public class IdempotentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentEventProcessor.class);

    private final IdempotencyStore idempotencyStore;
    private final MeterRegistry meterRegistry;

    public IdempotentEventProcessor(IdempotencyStore idempotencyStore, MeterRegistry meterRegistry) {
        this.idempotencyStore = idempotencyStore;
        this.meterRegistry = meterRegistry;
    }

    public void process(
            String consumerGroup,
            String topic,
            Map<String, Object> payload,
            String orderId,
            Consumer<Map<String, Object>> handler) {
        String eventId = EventEnvelope.extractEventId(payload);
        if (eventId == null || eventId.isBlank()) {
            eventId = fallbackEventId(payload, orderId);
        }
        TraceContext.setEventId(eventId);
        if (orderId != null) {
            TraceContext.setOrderId(orderId);
        }

        if (idempotencyStore.alreadyProcessed(consumerGroup, eventId)) {
            meterRegistry.counter("kafka.consume.duplicate", "topic", topic).increment();
            log.info(
                    "Skipping duplicate event group={} topic={} eventId={} orderId={}",
                    consumerGroup,
                    topic,
                    eventId,
                    orderId);
            return;
        }

        handler.accept(payload);
        idempotencyStore.markProcessed(consumerGroup, eventId, topic, orderId);
        meterRegistry.counter("kafka.consume.success", "topic", topic).increment();
    }

    private static String fallbackEventId(Map<String, Object> payload, String orderId) {
        Object eventType = payload != null ? payload.get("eventType") : null;
        Object timestamp = payload != null ? payload.get("timestamp") : null;
        if (orderId != null && eventType != null && timestamp != null) {
            return orderId + ":" + eventType + ":" + timestamp;
        }
        return EventEnvelope.newEventId();
    }
}
