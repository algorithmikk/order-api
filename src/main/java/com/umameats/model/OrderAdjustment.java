package com.umameats.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean
public class OrderAdjustment {
    /** MISSING, WRONG_ITEM, WRONG_ORDER, or UNDELIVERED. */
    private String reason;
    private String note;
    /** OPEN, WON, or LOST. */
    private String status;
    private Long customerAmountCents;
    private Long merchantAmountCents;
}
