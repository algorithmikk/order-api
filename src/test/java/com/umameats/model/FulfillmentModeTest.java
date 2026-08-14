package com.umameats.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FulfillmentModeTest {

    @Test
    void restaurantsDefaultToDriverProxyEvenWhenPartnered() {
        assertEquals(
                FulfillmentMode.DRIVER_PROXY,
                FulfillmentMode.resolve("MERCHANT_TYPE_RESTAURANT", null, "PARTNERED"));
    }

    @Test
    void proxyRestaurantsDispatchToDriverPool() {
        assertEquals(
                FulfillmentMode.DRIVER_PROXY,
                FulfillmentMode.resolve("MERCHANT_TYPE_RESTAURANT", null, "PROXY"));
    }

    @Test
    void kitchenPreparesIsOptInForPartneredRestaurantsOnly() {
        assertEquals(
                FulfillmentMode.MERCHANT_PREPARES,
                FulfillmentMode.resolve("MERCHANT_TYPE_RESTAURANT", "MERCHANT_PREPARES", "PARTNERED"));
        assertEquals(
                FulfillmentMode.DRIVER_PROXY,
                FulfillmentMode.resolve("MERCHANT_TYPE_RESTAURANT", "MERCHANT_PREPARES", "PROXY"));
    }

    @Test
    void groceryDefaultsToDriverShops() {
        assertEquals(
                FulfillmentMode.DRIVER_SHOPS,
                FulfillmentMode.resolve("MERCHANT_TYPE_GROCERY", null, "PROXY"));
    }
}
