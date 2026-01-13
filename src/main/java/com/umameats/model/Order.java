package com.umameats.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
    @DynamoDBIndexHashKey(globalSecondaryIndexName = "driver-orders-index")
    private String driverId;

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
    private Long totalAmount;  // Total charged to customer (subtotal + deliveryFee + serviceFee + tip)

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Long subtotal;  // Sum of item prices in cents

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Long serviceFee;  // Platform service fee in cents (5% of subtotal)

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Long platformFee;  // Platform commission in cents (15% of subtotal, taken from restaurant)

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Long taxAmount;  // Total tax in cents (GST+QST for Canada, VAT for Belgium/UAE)

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Double taxRate;  // Combined tax rate as decimal (e.g., 0.14975 for Quebec)

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String taxBreakdown;  // JSON string with tax breakdown (e.g., {"GST": 500, "QST": 997})

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String specialInstructions;


    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String paymentMethodId;

    @DynamoDBAttribute
    private BillingDetails billingDetails;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String storeName;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String storePhone;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String pickupAddress;

    // Restaurant/Store coordinates for delivery metrics
    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Double restaurantLat;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Double restaurantLng;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Long deliveryFee;  // Delivery fee in cents

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Long tip;  // Tip amount in cents

    // Delivery assignment fields
    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String assignedDriverId;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String assignedDriverName;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String assignedDriverPhone;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String deliveryStatus;  // UNASSIGNED, ASSIGNED, ACCEPTED, PICKED_UP, DELIVERED

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Long assignedAt;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Long acceptedAt;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.SS)
    private Set<String> declinedByDrivers;  // Set of driver IDs who declined this delivery
}


