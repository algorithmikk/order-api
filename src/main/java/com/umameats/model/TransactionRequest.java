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
    private BillingDetails billingDetails;  // Added
}
