package com.umameats.messaging.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;

/**
 * DynamoDB on-demand idempotency store. Table: umameats-processed-events (pk, ttl).
 */
public class DynamoIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(DynamoIdempotencyStore.class);

    public static final String DEFAULT_TABLE = "umameats-processed-events";

    private final DynamoDbTable<ProcessedEventEntity> table;
    private final long ttlDays;

    public DynamoIdempotencyStore(DynamoDbEnhancedClient enhancedClient, String tableName, long ttlDays) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(ProcessedEventEntity.class));
        this.ttlDays = ttlDays;
    }

    public DynamoIdempotencyStore(DynamoDbEnhancedClient enhancedClient) {
        this(enhancedClient, DEFAULT_TABLE, 14);
    }

    static String pk(String consumerGroup, String eventId) {
        return consumerGroup + "#" + eventId;
    }

    @Override
    public boolean alreadyProcessed(String consumerGroup, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        ProcessedEventEntity existing = table.getItem(
                Key.builder().partitionValue(pk(consumerGroup, eventId)).build());
        return existing != null;
    }

    @Override
    public void markProcessed(String consumerGroup, String eventId, String topic, String orderId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        ProcessedEventEntity entity = new ProcessedEventEntity();
        entity.setPk(pk(consumerGroup, eventId));
        entity.setConsumerGroup(consumerGroup);
        entity.setEventId(eventId);
        entity.setTopic(topic);
        entity.setOrderId(orderId);
        entity.setProcessedAt(System.currentTimeMillis());
        entity.setTtlEpochSeconds(Instant.now().plusSeconds(ttlDays * 24 * 3600).getEpochSecond());
        try {
            table.putItem(entity);
        } catch (ConditionalCheckFailedException e) {
            log.debug("Event already marked processed: group={} eventId={}", consumerGroup, eventId);
        }
    }
}
