package com.umameats.notify;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory skip window so the same milestone is not blasted twice when both
 * customer.notifications and delivery.events fire for one status change.
 * desiredCount=1 makes a process-local map enough.
 */
@Component
public class NotificationDedupe {

    private static final long WINDOW_MS = 15_000L;
    private final ConcurrentHashMap<String, Long> recent = new ConcurrentHashMap<>();

    public boolean shouldSend(String recipientId, String orderId, String type) {
        if (recipientId == null || orderId == null || type == null) {
            return true;
        }
        String key = recipientId + "|" + orderId + "|" + type;
        long now = System.currentTimeMillis();
        Long previous = recent.put(key, now);
        prune(now);
        return previous == null || now - previous > WINDOW_MS;
    }

    private void prune(long now) {
        if (recent.size() < 500) {
            return;
        }
        Iterator<Map.Entry<String, Long>> iterator = recent.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > WINDOW_MS) {
                iterator.remove();
            }
        }
    }
}
