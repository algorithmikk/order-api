package com.umameats.support.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * A single turn in a support thread. Tool calls are persisted alongside the text
 * so an ops agent taking over sees exactly what the agent did on the user's behalf.
 */
@Data
@DynamoDbBean
public class SupportMessage {

    private String threadId;
    /** {@code <epochMillis>#<uuid>} so the sort key orders chronologically. */
    private String messageId;
    /** USER | AGENT | HUMAN_AGENT | SYSTEM */
    private String sender;
    private String body;
    /** Human-readable summary of tools the agent ran for this turn, e.g. "getOrderStatus". */
    private String toolTrace;
    private Long createdAt;

    @DynamoDbPartitionKey
    public String getThreadId() {
        return threadId;
    }

    @DynamoDbSortKey
    public String getMessageId() {
        return messageId;
    }
}
