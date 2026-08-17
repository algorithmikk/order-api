package com.umameats.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NotificationCatalogTest {

    @Test
    void confirmedStartsCustomerLiveActivity() {
        NotificationCatalog.Copy copy = NotificationCatalog.customer(
                "ORDER_CONFIRMED", "CREATED", "Demo Kitchen", null, null);
        assertNotNull(copy);
        assertEquals("ORDER_CONFIRMED", copy.type);
        assertTrue(copy.sendBanner);
        assertEquals(NotificationCatalog.LiveAction.START, copy.liveAction);
        assertEquals("confirmed", copy.phase);
        assertTrue(copy.body.contains("Demo Kitchen"));
    }

    @Test
    void preparingAndReadyAreDistinctMilestones() {
        NotificationCatalog.Copy preparing = NotificationCatalog.customer(
                "ORDER_STATUS_UPDATED", "PREPARING", "Demo Kitchen", null, null);
        NotificationCatalog.Copy ready = NotificationCatalog.customer(
                "ORDER_STATUS_READY_FOR_PICKUP", "READY_FOR_PICKUP", "Demo Kitchen", null, null);
        assertEquals("preparing", preparing.phase);
        assertEquals("ready", ready.phase);
        assertTrue(ready.body.toLowerCase().contains("driver") || ready.title.toLowerCase().contains("ready"));
    }

    @Test
    void driverAssignedUsesFirstName() {
        NotificationCatalog.Copy copy = NotificationCatalog.customer(
                "DELIVERY_ASSIGNED", "DRIVER_EN_ROUTE_TO_STORE", "Demo Kitchen", "Alex Driver", 12);
        assertEquals("DRIVER_ASSIGNED", copy.type);
        assertTrue(copy.title.startsWith("Alex"));
        assertEquals("assigned", copy.phase);
    }

    @Test
    void etaTicksAreSilentLiveUpdates() {
        NotificationCatalog.Copy copy = NotificationCatalog.customer(
                "DELIVERY_ETA", "OUT_FOR_DELIVERY", "Demo Kitchen", "Alex", 8);
        assertFalse(copy.sendBanner);
        assertEquals(NotificationCatalog.LiveAction.UPDATE, copy.liveAction);
        assertEquals("LIVE_ETA", copy.type);
    }

    @Test
    void deliveredEndsLiveActivity() {
        NotificationCatalog.Copy copy = NotificationCatalog.customer(
                "ORDER_STATUS_DELIVERED", "DELIVERED", "Demo Kitchen", "Alex", 0);
        assertEquals("ORDER_DELIVERED", copy.type);
        assertEquals(NotificationCatalog.LiveAction.END, copy.liveAction);
    }

    @Test
    void driverOfferIsTimeSensitive() {
        NotificationCatalog.Copy copy = NotificationCatalog.driverOffer(false, "Demo Kitchen", null);
        assertEquals("NEW_DELIVERY", copy.type);
        assertEquals("time-sensitive", copy.interruptionLevel);
        assertEquals(NotificationCatalog.LiveAction.START, copy.liveAction);

        NotificationCatalog.Copy soft = NotificationCatalog.driverOffer(true, "Demo Kitchen", 90);
        assertEquals("SOFT_OFFER", soft.type);
        assertTrue(soft.body.contains("90"));
    }

    @Test
    void driverTripAssignedStartsActiveLive() {
        NotificationCatalog.Copy copy = NotificationCatalog.driverTrip(
                "DELIVERY_ASSIGNED", "DRIVER_EN_ROUTE_TO_STORE", "Demo Kitchen", 15);
        assertEquals(NotificationCatalog.LiveAction.START, copy.liveAction);
        assertEquals("to_store", copy.phase);
        assertFalse(copy.sendBanner);
    }
}
