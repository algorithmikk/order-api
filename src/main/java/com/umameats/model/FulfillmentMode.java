package com.umameats.model;

public final class FulfillmentMode {
    public static final String MERCHANT_PREPARES = "MERCHANT_PREPARES";
    public static final String DRIVER_SHOPS = "DRIVER_SHOPS";

    private FulfillmentMode() {}

    public static boolean isDriverShops(String mode) {
        return DRIVER_SHOPS.equals(mode);
    }
}
