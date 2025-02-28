package com.umameats.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeliveryEvent {
    // Core identifiers
    private String deliveryId;
    private String orderId;
    private String customerId;
    private String restaurantId;
    
    // Status information
    private DeliveryStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime estimatedPickupTime;
    private LocalDateTime estimatedDeliveryTime;
    
    // Location information
    private Location pickupLocation;
    private Location deliveryLocation;
    private Location currentDriverLocation; // For tracking
    
    // ETA information
    private Integer estimatedTimeInMinutes;
    private Double distanceInKilometers;
    
    // Driver information (might be null initially)
    private DriverInfo driverInfo;
    
    // Order details (for context)
    private OrderInfo orderInfo;
    
    // Custom instructions
    private String specialInstructions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private Double latitude;
        private Double longitude;
        private String address;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private String formattedAddress;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverInfo {
        private String driverId;
        private String name;
        private String phoneNumber;
        private String vehicleType;
        private String vehicleColor;
        private String licensePlate;
        private Double rating;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderInfo {
        private String orderId;
        private List<OrderItem> items;
        private Double orderTotal;
        private Boolean isPrepaid;
        private String paymentMethod;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private String itemId;
        private String itemName;
        private Integer quantity;
        private String specialInstructions;
    }
    
    public enum DeliveryStatus {
        PENDING_ASSIGNMENT,    // No driver assigned yet
        DRIVER_ASSIGNED,       // Driver assigned but not yet accepted
        DRIVER_ACCEPTED,       // Driver has accepted the delivery
        DRIVER_EN_ROUTE_TO_PICKUP, // Driver heading to restaurant
        ARRIVED_AT_PICKUP,     // Driver at restaurant
        PICKED_UP,             // Food picked up, heading to customer
        EN_ROUTE_TO_DELIVERY,  // Driver on the way to customer
        ARRIVED_AT_DELIVERY,   // Driver at customer location
        DELIVERED,             // Successfully delivered
        DELIVERY_FAILED,       // Couldn't deliver (various reasons)
        CANCELLED              // Order was cancelled
    }
}
