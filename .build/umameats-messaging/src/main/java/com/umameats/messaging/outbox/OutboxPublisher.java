package com.umameats.messaging.outbox;

import com.umameats.messaging.KafkaProducerSupport;
import com.umameats.messaging.TraceContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Polls PENDING outbox rows and publishes them to Kafka with backoff.
 */
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxWriter outboxWriter;
    private final KafkaProducerSupport kafkaProducerSupport;
    private final MeterRegistry meterRegistry;
    private final int maxAttempts;
    private final int batchSize;

    public OutboxPublisher(
            OutboxWriter outboxWriter,
            KafkaProducerSupport kafkaProducerSupport,
            MeterRegistry meterRegistry,
            int maxAttempts,
            int batchSize) {
        this.outboxWriter = outboxWriter;
        this.kafkaProducerSupport = kafkaProducerSupport;
        this.meterRegistry = meterRegistry;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${umameats.messaging.outbox.poll-interval-ms:2000}")
    public void publishPending() {
        long now = System.currentTimeMillis();
        List<OutboxRecordEntity> pending = loadDue(now);
        meterRegistry.gauge("outbox.pending", pending.size());

        for (OutboxRecordEntity record : pending) {
            try {
                if (record.getTraceId() != null) {
                    TraceContext.setTraceId(record.getTraceId());
                }
                kafkaProducerSupport
                        .sendRaw(
                                record.getTopic(),
                                record.getMessageKey(),
                                record.getPayloadJson(),
                                record.getEventType(),
                                record.getEventId())
                        .get();
                record.setStatus(OutboxRecordEntity.STATUS_PUBLISHED);
                record.setUpdatedAt(System.currentTimeMillis());
                record.setNextAttemptAt(Long.MAX_VALUE);
                outboxWriter.table().putItem(record);
                meterRegistry.counter("outbox.publish.success").increment();
            } catch (Exception e) {
                int attempts = record.getAttemptCount() + 1;
                record.setAttemptCount(attempts);
                record.setLastError(truncate(e.getMessage(), 500));
                record.setUpdatedAt(System.currentTimeMillis());
                if (attempts >= maxAttempts) {
                    record.setStatus(OutboxRecordEntity.STATUS_DEAD);
                    record.setNextAttemptAt(Long.MAX_VALUE);
                    meterRegistry.counter("outbox.dead").increment();
                    log.error(
                            "Outbox event moved to DEAD outboxId={} topic={} attempts={}",
                            record.getOutboxId(),
                            record.getTopic(),
                            attempts,
                            e);
                } else {
                    long backoffMs = (long) Math.min(300_000, Math.pow(2, attempts) * 1000L);
                    record.setNextAttemptAt(System.currentTimeMillis() + backoffMs);
                    meterRegistry.counter("outbox.publish.failure").increment();
                    log.warn(
                            "Outbox publish failed outboxId={} topic={} attempts={} nextAttemptAt={}",
                            record.getOutboxId(),
                            record.getTopic(),
                            attempts,
                            record.getNextAttemptAt(),
                            e);
                }
                outboxWriter.table().putItem(record);
            } finally {
                TraceContext.clear();
            }
        }
    }

    private List<OutboxRecordEntity> loadDue(long now) {
        List<OutboxRecordEntity> results = new ArrayList<>();
        try {
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                    .queryConditional(QueryConditional.keyEqualTo(
                            Key.builder().partitionValue(OutboxRecordEntity.STATUS_PENDING).build()))
                    .limit(batchSize)
                    .build();
            outboxWriter
                    .table()
                    .index(OutboxRecordEntity.STATUS_INDEX)
                    .query(request)
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .filter(item -> item.getNextAttemptAt() <= now)
                    .limit(batchSize)
                    .forEach(results::add);
        } catch (Exception e) {
            log.warn("Failed to query outbox pending items: {}", e.getMessage());
        }
        return results;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
