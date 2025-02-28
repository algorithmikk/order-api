package com.umameats.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Generic event request envelope that can contain any type of event payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventRequest {
    /**
     * Type of event (e.g., DELIVERY_EVENT, PAYMENT_EVENT, ORDER_UPDATED)
     */
    private String eventType;
    
    /**
     * Service that generated the event (e.g., order-service, payment-service)
     */
    private String eventSource;
    
    /**
     * The actual event data - could be DeliveryEvent or any other event object
     */
    private Object payload;
    
    /**
     * When the event was created
     */
    private LocalDateTime timestamp;
}