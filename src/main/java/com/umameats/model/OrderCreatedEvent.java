package com.umameats.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Order Created Event
 * 
 * Published to Kafka topic 'umameats.order.events' when a new order is created.
 * Consumed by delivery-orchestration-api for driver assignment.
 * 
 * @author UmaEats Engineering
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private String orderId;
    private String restaurantId;
    private Double restaurantLat;
    private Double restaurantLng;
    private String customerId;
    private Double customerLat;
    private Double customerLng;
    private Long createdAt;
    private String deliveryPreference;
}

