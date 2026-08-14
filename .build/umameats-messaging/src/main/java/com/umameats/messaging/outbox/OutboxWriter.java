package com.umameats.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umameats.messaging.EventEnvelope;
import com.umameats.messaging.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Writes durable outbox rows so Kafka publish can be retried after DB commit.
 */
public class OutboxWriter {

    private static final Logger log = LoggerFactory.getLogger(OutboxWriter.class);

    public static final String DEFAULT_TABLE = "umameats-event-outbox";

    private final DynamoDbTable<OutboxRecordEntity> table;
    private final ObjectMapper objectMapper;
    private final long ttlDays;

    public OutboxWriter(DynamoDbEnhancedClient enhancedClient, ObjectMapper objectMapper, String tableName, long ttlDays) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(OutboxRecordEntity.class));
        this.objectMapper = objectMapper;
        this.ttlDays = ttlDays;
    }

    public OutboxWriter(DynamoDbEnhancedClient enhancedClient, ObjectMapper objectMapper) {
        this(enhancedClient, objectMapper, DEFAULT_TABLE, 30);
    }

    public OutboxRecordEntity enqueue(String topic, String messageKey, Object payload, String eventType) {
        try {
            Map<String, Object> body;
            if (payload instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) map;
                body = EventEnvelope.enrich(cast, eventType);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> asMap = objectMapper.convertValue(payload, Map.class);
                body = EventEnvelope.enrich(asMap, eventType);
            }
            String json = objectMapper.writeValueAsString(body);
            long now = System.currentTimeMillis();

            OutboxRecordEntity record = new OutboxRecordEntity();
            record.setOutboxId(UUID.randomUUID().toString());
            record.setStatus(OutboxRecordEntity.STATUS_PENDING);
            record.setNextAttemptAt(now);
            record.setTopic(topic);
            record.setMessageKey(messageKey);
            record.setPayloadJson(json);
            record.setEventType(eventType);
            record.setEventId(EventEnvelope.extractEventId(body));
            record.setTraceId(TraceContext.currentTraceId());
            record.setAttemptCount(0);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            record.setTtlEpochSeconds(Instant.now().plusSeconds(ttlDays * 24 * 3600).getEpochSecond());
            table.putItem(record);
            log.info(
                    "Enqueued outbox event outboxId={} topic={} key={} eventType={} eventId={}",
                    record.getOutboxId(),
                    topic,
                    messageKey,
                    eventType,
                    record.getEventId());
            return record;
        } catch (Exception e) {
            // Callers surface only the wrapper's message, so log the cause here or
            // the real failure never reaches CloudWatch.
            log.error("Failed to enqueue outbox event topic={} key={} eventType={}",
                    topic, messageKey, eventType, e);
            throw new IllegalStateException("Failed to enqueue outbox event topic=" + topic, e);
        }
    }

    DynamoDbTable<OutboxRecordEntity> table() {
        return table;
    }
}
