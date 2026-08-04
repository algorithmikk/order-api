package com.umameats.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.umameats.service.LocalDateTimeAttributeConverter;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

@Data
@DynamoDbBean
public class Order {
    private String orderId;
    private String storeId;
    private String customerId;
    private String driverId;
    private String paymentIntentId;
    private List<OrderItem> items;
    private DeliveryAddress deliveryAddress;
    private String paymentMethod;
    private OrderStatus status;
    private LocalDateTime orderDate;
    private Long totalAmount;
    private Long subtotal;
    private Long serviceFee;
    private Long platformFee;
    private Long taxAmount;
    private Double taxRate;
    private String taxBreakdown;
    private String specialInstructions;
    private String paymentMethodId;
    private BillingDetails billingDetails;
    private String storeName;
    private String storePhone;
    private String pickupAddress;
    private Double restaurantLat;
    private Double restaurantLng;
    private Long deliveryFee;
    private Long tip;
    private String assignedDriverId;
    private String assignedDriverName;
    private String assignedDriverPhone;
    private String deliveryStatus;
    private Long assignedAt;
    private Long acceptedAt;
    private Set<String> declinedByDrivers;

    /** MERCHANT_PREPARES | DRIVER_SHOPS — snapshot from store at create. */
    private String fulfillmentMode;
    /** Canonical MERCHANT_TYPE_* snapshot. */
    private String merchantType;
    /** CONTACT | BEST_MATCH | REFUND */
    private String customerSubstitutionPreference;
    private Boolean requiresIsothermalBag;
    private Long shoppingStartedAt;
    private Long shoppingCompletedAt;
    private Boolean isothermalBagConfirmed;
    private String bagPhotoUrl;
    /** JSON or comma-separated checklist keys completed by driver. */
    private String coldChainChecklist;
    private Long qualityConfirmedAt;
    private String proofOfDeliveryUrl;
    private String deliveryPin;
    private Long deliveredAt;

    @DynamoDbPartitionKey
    public String getOrderId() {
        return orderId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "store-orders-index")
    public String getStoreId() {
        return storeId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "customer-orders-index")
    public String getCustomerId() {
        return customerId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "driver-orders-index")
    public String getDriverId() {
        return driverId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "status-index")
    public OrderStatus getStatus() {
        return status;
    }

    @DynamoDbConvertedBy(LocalDateTimeAttributeConverter.class)
    public LocalDateTime getOrderDate() {
        return orderDate;
    }
}
