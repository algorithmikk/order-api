package com.umameats.model;

import lombok.Data;

@Data
public class TransactionResponse {
    private String transactionId;
    private String status;
    private String clientSecret;  // For Stripe
}
