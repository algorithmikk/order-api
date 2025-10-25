package com.umameats.model;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_FAILED,
    CREATED,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    DRIVER_EN_ROUTE_TO_STORE,  // Driver accepted and heading to restaurant
    OUT_FOR_DELIVERY,           // Driver picked up food and heading to customer
    DELIVERED,
    CANCELLED
}
