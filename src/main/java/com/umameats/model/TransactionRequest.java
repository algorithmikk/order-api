package com.umameats.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionRequest {
    private String orderId;
    private String customerId;
    private String storeId;
    private Long amount;
    private String paymentMethodId;
    private String currency;
    private BillingDetails billingDetails;
    private String connectedAccountId;  // Store's Stripe Connect account ID for direct transfer

    // Payment split fields
    private Long subtotal;      // Food cost in cents
    private Long deliveryFee;   // Delivery fee in cents (for driver)
    private Long tipAmount;     // Tip in cents (for driver)
    private Long serviceFee;    // Service fee in cents (for platform)
}
