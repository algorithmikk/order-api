package com.umameats.model;

import lombok.Data;
import java.util.List;

/**
 * Request DTO for pricing calculation.
 * Used by frontend to get pricing before checkout.
 */
@Data
public class PricingRequest {
    private String storeId;
    private List<OrderItem> items;
    private Double deliveryDistanceKm;  // Optional - if not provided, uses subtotal-based calculation
    private Long tipCents;              // Optional - customer-selected tip
}

