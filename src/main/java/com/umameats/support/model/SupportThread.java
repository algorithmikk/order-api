package com.umameats.support.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

/**
 * One support conversation. A principal has at most one thread in
 * {@link SupportThreadState#AI} or {@link SupportThreadState#WAITING_HUMAN} at a
 * time; resolving it starts a fresh thread on the next question.
 */
@Data
@DynamoDbBean
public class SupportThread {

    private String threadId;
    private String principalId;
    private String principalRole;
    private String state;
    /** Order the conversation is about, when the user opened support from an order. */
    private String orderId;
    private String subject;
    private String locale;
    private Long createdAt;
    private Long updatedAt;
    /** Set when the agent (or the user) asked for a human. */
    private Long escalatedAt;
    private String escalationReason;
    /** Guards against the agent refunding the same order twice across turns. */
    private Long refundedCents;

    @DynamoDbPartitionKey
    public String getThreadId() {
        return threadId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "support-principal-index")
    public String getPrincipalId() {
        return principalId;
    }

    @DynamoDbSecondarySortKey(indexNames = "support-principal-index")
    public Long getUpdatedAt() {
        return updatedAt;
    }
}
