package com.umameats.chat.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * One customer/driver message on an order.
 *
 * <p>Field-for-field identical to the bean in umameats-driver-api so both
 * services can share the {@code umameats-delivery-chat} table. That is what lets
 * already-shipped driver builds keep using the legacy driver-api endpoint while
 * new builds move to the unified one, with no migration in between.
 */
@Data
@DynamoDbBean
public class ChatMessage {

    private String orderId;
    /** {@code <epochMillis>#<uuid>} so the sort key orders chronologically. */
    private String messageId;
    private String senderId;
    /** DRIVER | CUSTOMER | STORE | OPS */
    private String senderRole;
    private String body;
    private Long createdAt;

    @DynamoDbPartitionKey
    public String getOrderId() {
        return orderId;
    }

    @DynamoDbSortKey
    public String getMessageId() {
        return messageId;
    }
}
