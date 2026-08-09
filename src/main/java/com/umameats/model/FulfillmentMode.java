package com.umameats.model;

/**
 * How an order is fulfilled at a store.
 * <ul>
 *   <li>{@link #MERCHANT_PREPARES} — kitchen packs; driver picks up (partnered restaurants).</li>
 *   <li>{@link #DRIVER_SHOPS} — driver shops the list in-store (grocery / convenience).</li>
 *   <li>{@link #DRIVER_PROXY} — launch proxy: driver places/pays at unregistered restaurant,
 *       then picks up and delivers (no kitchen confirm).</li>
 * </ul>
 */
public final class FulfillmentMode {
    public static final String MERCHANT_PREPARES = "MERCHANT_PREPARES";
    public static final String DRIVER_SHOPS = "DRIVER_SHOPS";
    /** Temporary launch mode for PROXY (unregistered) restaurants. */
    public static final String DRIVER_PROXY = "DRIVER_PROXY";

    private FulfillmentMode() {}

    public static boolean isDriverShops(String mode) {
        return DRIVER_SHOPS.equals(mode);
    }

    public static boolean isDriverProxy(String mode) {
        return DRIVER_PROXY.equals(mode);
    }

    /** Paid orders open the driver marketplace without waiting for kitchen READY. */
    public static boolean isImmediateDispatch(String mode) {
        return isDriverShops(mode) || isDriverProxy(mode);
    }
}
