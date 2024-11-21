package com.umameats.model;

import lombok.Data;

@Data
public class TransactionRequest {
    private String orderId;
    private String customerId;
    private String storeId;
    private Double amount;
    private String paymentMethod;
}
