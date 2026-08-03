package com.umameats.model;

import lombok.Data;

/**
 * Ops-only request to create a paid-looking demo order without Stripe.
 */
@Data
public class SeedDemoOrderRequest {
    private String customerId;
    private String storeId;
    /** CREATED (default) or READY */
    private String mode;
    private Long tipCents;
}
