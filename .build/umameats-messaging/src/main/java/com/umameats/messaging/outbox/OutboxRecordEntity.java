package com.umameats.messaging.outbox;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

/**
 * Transactional outbox row. GSI status-nextAttempt-index for pending polls.
 */
@DynamoDbBean
public class OutboxRecordEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DEAD = "DEAD";
    public static final String STATUS_INDEX = "status-nextAttempt-index";

    private String outboxId;
    private String status;
    private long nextAttemptAt;
    private String topic;
    private String messageKey;
    private String payloadJson;
    private String eventType;
    private String eventId;
    private String traceId;
    private int attemptCount;
    private String lastError;
    private long createdAt;
    private long updatedAt;
    private long ttlEpochSeconds;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("outboxId")
    public String getOutboxId() {
        return outboxId;
    }

    public void setOutboxId(String outboxId) {
        this.outboxId = outboxId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = STATUS_INDEX)
    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDbSecondarySortKey(indexNames = STATUS_INDEX)
    @DynamoDbAttribute("nextAttemptAt")
    public long getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(long nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    @DynamoDbAttribute("topic")
    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    @DynamoDbAttribute("messageKey")
    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    @DynamoDbAttribute("payloadJson")
    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    @DynamoDbAttribute("eventType")
    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    @DynamoDbAttribute("eventId")
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    @DynamoDbAttribute("traceId")
    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    @DynamoDbAttribute("attemptCount")
    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    @DynamoDbAttribute("lastError")
    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    @DynamoDbAttribute("createdAt")
    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("updatedAt")
    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @DynamoDbAttribute("ttl")
    public long getTtlEpochSeconds() {
        return ttlEpochSeconds;
    }

    public void setTtlEpochSeconds(long ttlEpochSeconds) {
        this.ttlEpochSeconds = ttlEpochSeconds;
    }
}
