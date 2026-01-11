package com.umameats.model;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for pricing calculation.
 * All amounts are in CENTS.
 */
@Data
@Builder
public class PricingResponse {
    private Long subtotal;       // Sum of item prices
    private Long deliveryFee;    // Delivery fee
    private Long serviceFee;     // Platform service fee (charged to customer)
    private Long tip;            // Customer tip for driver
    private Long totalAmount;    // subtotal + deliveryFee + serviceFee + tip
    private Long platformFee;    // Platform commission (for info, taken from restaurant)
    
    // Breakdown for display
    private Long restaurantPayout;  // What restaurant receives (subtotal - platformFee)
    private Long driverPayout;      // What driver receives (deliveryFee + tip)
}

