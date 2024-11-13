package com.umameats.model;

import java.time.LocalDateTime;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;

import lombok.Data;

@Data
@DynamoDBTable(tableName = "umameats-orders")
public class Order {
    @DynamoDBHashKey
    private String orderId;

    @DynamoDBRangeKey
    private String customerId;

    @DynamoDBAttribute
    private String storeId;

    @DynamoDBAttribute
    private List<OrderItem> items;

    @DynamoDBAttribute
    private DeliveryAddress deliveryAddress;

    @DynamoDBAttribute
    private String paymentMethod;

    @DynamoDBAttribute
    @DynamoDBTypeConvertedEnum
    private OrderStatus status;

    @DynamoDBAttribute
    private LocalDateTime orderDate;

    @DynamoDBAttribute
    private Double totalAmount;

    @DynamoDBAttribute
    private String specialInstructions;
}

