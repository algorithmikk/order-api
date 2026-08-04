package com.umameats.model;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_FAILED,
    CREATED,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    DRIVER_EN_ROUTE_TO_STORE,
    /** Driver is shopping the grocery list in-store. */
    DRIVER_SHOPPING,
    /** All line items resolved; waiting on customer to approve picks/substitutions or request changes. */
    AWAITING_SHOPPING_APPROVAL,
    /** All line items resolved; bag/quality gate may proceed. */
    SHOPPING_COMPLETE,
    PICKED_UP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}
