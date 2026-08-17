package com.umameats.notify;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NotificationDedupeTest {

    @Test
    void skipsSameMilestoneInsideWindow() {
        NotificationDedupe dedupe = new NotificationDedupe();
        assertTrue(dedupe.shouldSend("c1", "o1", "ORDER_CONFIRMED"));
        assertFalse(dedupe.shouldSend("c1", "o1", "ORDER_CONFIRMED"));
        assertTrue(dedupe.shouldSend("c1", "o1", "DRIVER_ASSIGNED"));
        assertTrue(dedupe.shouldSend("c2", "o1", "ORDER_CONFIRMED"));
    }
}
