package com.umameats.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean
public class OrderItem {
    private String itemId;
    private String itemName;
    private Integer quantity;
    private Double price;
    private String specialInstructions;

    /** PENDING | FOUND | SUBSTITUTED | UNAVAILABLE */
    private String pickStatus;
    private Integer pickedQuantity;
    private String substituteItemId;
    private String substituteName;
    private Double unitPricePaid;
    private String aisle;
    /** AMBIENT | CHILLED | FROZEN */
    private String temperatureClass;
    private String barcode;
}
