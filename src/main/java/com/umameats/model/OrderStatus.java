package com.umameats.model;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_FAILED,
    CREATED,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}
