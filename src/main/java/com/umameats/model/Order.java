package com.umameats.model;

import java.time.LocalDateTime;
import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperFieldModel.DynamoDBAttributeType;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTyped;
import com.umameats.service.LocalDateTimeConverter;

import lombok.Data;


@Data
@DynamoDBTable(tableName = "umameats-orders")
public class Order {
   @DynamoDBHashKey
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String orderId;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    @DynamoDBIndexHashKey(globalSecondaryIndexName = "store-orders-index")
    private String storeId;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    @DynamoDBIndexHashKey(globalSecondaryIndexName = "customer-orders-index")
    private String customerId;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String paymentIntentId;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.L)
    private List<OrderItem> items;

    @DynamoDBAttribute
    private DeliveryAddress deliveryAddress;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String paymentMethod;

    @DynamoDBAttribute
    @DynamoDBTypeConvertedEnum
    private OrderStatus status;

    @DynamoDBAttribute
    @DynamoDBTypeConverted(converter = LocalDateTimeConverter.class)
    private LocalDateTime orderDate;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Long totalAmount;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String specialInstructions;


    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String paymentMethodId;

    @DynamoDBAttribute
    private BillingDetails billingDetails;
}


