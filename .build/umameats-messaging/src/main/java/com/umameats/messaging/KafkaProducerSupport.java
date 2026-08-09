package com.umameats.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Produces String JSON events with standard UmaMeats tracing headers and metrics.
 */
public class KafkaProducerSupport {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerSupport.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final long sendTimeoutSeconds;

    public KafkaProducerSupport(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this(kafkaTemplate, objectMapper, meterRegistry, 30);
    }

    public KafkaProducerSupport(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            long sendTimeoutSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    public CompletableFuture<SendResult<String, String>> sendJson(
            String topic,
            String key,
            Object payload,
            String eventType) {
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
            String eventId = EventEnvelope.extractEventId(body);
            String json = objectMapper.writeValueAsString(body);
            return sendRaw(topic, key, json, eventType, eventId);
        } catch (Exception e) {
            meterRegistry.counter("kafka.produce.failure", "topic", topic, "reason", "serialize").increment();
            log.error("Failed to serialize Kafka payload topic={} key={}", topic, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<SendResult<String, String>> sendRaw(
            String topic,
            String key,
            String json,
            String eventType,
            String eventId) {
        String resolvedEventId = (eventId == null || eventId.isBlank()) ? EventEnvelope.newEventId() : eventId;
        String traceId = TraceContext.currentTraceId();
        String correlationId = TraceContext.currentCorrelationId();

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
        record.headers().add(header(MessagingHeaders.TRACE_ID, traceId));
        record.headers().add(header(MessagingHeaders.CORRELATION_ID, correlationId));
        record.headers().add(header(MessagingHeaders.EVENT_ID, resolvedEventId));
        record.headers().add(header(MessagingHeaders.SCHEMA_VERSION, EventEnvelope.SCHEMA_VERSION));
        if (eventType != null) {
            record.headers().add(header(MessagingHeaders.EVENT_TYPE, eventType));
        }

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                meterRegistry.counter("kafka.produce.success", "topic", topic).increment();
                log.info(
                        "Published Kafka event topic={} key={} eventId={} eventType={} partition={} offset={} traceId={}",
                        topic,
                        key,
                        resolvedEventId,
                        eventType,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        traceId);
            } else {
                meterRegistry.counter("kafka.produce.failure", "topic", topic, "reason", "send").increment();
                log.error(
                        "Failed to publish Kafka event topic={} key={} eventId={} eventType={} traceId={}",
                        topic,
                        key,
                        resolvedEventId,
                        eventType,
                        traceId,
                        ex);
            }
        });
        return future;
    }

    public void sendJsonSync(String topic, String key, Object payload, String eventType) {
        try {
            sendJson(topic, key, payload, eventType).get(sendTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka publish failed for topic=" + topic + " key=" + key, e);
        }
    }

    private static RecordHeader header(String name, String value) {
        return new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
