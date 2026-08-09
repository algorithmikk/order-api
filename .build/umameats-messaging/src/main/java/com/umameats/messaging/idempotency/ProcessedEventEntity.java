package com.umameats.messaging.idempotency;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * DynamoDB item for processed Kafka events (on-demand table).
 */
@DynamoDbBean
public class ProcessedEventEntity {

    private String pk;
    private String consumerGroup;
    private String eventId;
    private String topic;
    private String orderId;
    private long processedAt;
    private long ttlEpochSeconds;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("pk")
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbAttribute("consumerGroup")
    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    @DynamoDbAttribute("eventId")
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    @DynamoDbAttribute("topic")
    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    @DynamoDbAttribute("orderId")
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @DynamoDbAttribute("processedAt")
    public long getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(long processedAt) {
        this.processedAt = processedAt;
    }

    @DynamoDbAttribute("ttl")
    public long getTtlEpochSeconds() {
        return ttlEpochSeconds;
    }

    public void setTtlEpochSeconds(long ttlEpochSeconds) {
        this.ttlEpochSeconds = ttlEpochSeconds;
    }
}
