package com.umameats.model;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_FAILED,
    CREATED,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    DRIVER_EN_ROUTE_TO_STORE,  // Driver accepted and heading to restaurant
    PICKED_UP,                  // Driver confirmed pickup at restaurant
    OUT_FOR_DELIVERY,           // Driver heading to customer with food
    DELIVERED,
    CANCELLED
}
