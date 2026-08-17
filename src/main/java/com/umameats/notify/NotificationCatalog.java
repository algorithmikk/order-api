package com.umameats.notify;

/**
 * Single mapping from Kafka eventType / order status to user-facing copy and
 * live-notification phase. Driver-api has an identical class so both apps stay
 * in sync without publishing a new messaging JAR.
 */
public final class NotificationCatalog {

    public enum LiveAction {
        NONE,
        START,
        UPDATE,
        END
    }

    public static final class Copy {
        public final String type;
        public final String title;
        public final String body;
        public final String channelId;
        public final String interruptionLevel;
        public final boolean sendBanner;
        public final LiveAction liveAction;
        public final String phase;
        public final int progressPercent;
        public final String liveTitle;
        public final String liveSubtitle;
        public final Integer etaMinutes;

        Copy(
                String type,
                String title,
                String body,
                String channelId,
                String interruptionLevel,
                boolean sendBanner,
                LiveAction liveAction,
                String phase,
                int progressPercent,
                String liveTitle,
                String liveSubtitle,
                Integer etaMinutes) {
            this.type = type;
            this.title = title;
            this.body = body;
            this.channelId = channelId;
            this.interruptionLevel = interruptionLevel;
            this.sendBanner = sendBanner;
            this.liveAction = liveAction;
            this.phase = phase;
            this.progressPercent = progressPercent;
            this.liveTitle = liveTitle;
            this.liveSubtitle = liveSubtitle;
            this.etaMinutes = etaMinutes;
        }
    }

    private NotificationCatalog() {
    }

    public static String firstName(String fullName, String fallback) {
        if (fullName == null || fullName.isBlank()) {
            return fallback;
        }
        return fullName.trim().split("\\s+")[0];
    }

    public static String storeLabel(String storeName) {
        return storeName == null || storeName.isBlank() ? "A store" : storeName;
    }

    /**
     * Customer milestone + live journey for an order lifecycle event.
     *
     * @param eventType Kafka eventType (ORDER_CONFIRMED, DELIVERY_ASSIGNED, ORDER_STATUS_PREPARING, …)
     * @param status    canonical order status when known
     */
    public static Copy customer(String eventType, String status, String storeName, String driverName, Integer etaMinutes) {
        String t = upper(eventType);
        String s = upper(status);
        String store = storeLabel(storeName);
        String driver = firstName(driverName, "Your driver");
        String eta = etaLabel(etaMinutes);

        if (contains(t, "PAYMENT_FAILED") || "PAYMENT_FAILED".equals(s)) {
            return banner("PAYMENT_FAILED", "Payment failed",
                    "We couldn’t complete payment for your order. Try another card.",
                    "orders", "active", LiveAction.NONE, "failed", 0, "Payment failed", "", null);
        }
        if (contains(t, "CANCEL") || "CANCELLED".equals(s)) {
            return banner("ORDER_CANCELLED", "Order cancelled",
                    "Your order from " + store + " was cancelled.",
                    "orders", "active", LiveAction.END, "cancelled", 100, "Order cancelled", store, etaMinutes);
        }
        if (isDelivered(t, s)) {
            return banner("ORDER_DELIVERED", "Delivered",
                    "Enjoy your order from " + store + ".",
                    "orders", "active", LiveAction.END, "delivered", 100, "Delivered", store, etaMinutes);
        }
        if (contains(t, "ETA") || contains(t, "DELIVERY_ETA")) {
            return silent("LIVE_ETA", LiveAction.UPDATE, phaseFromStatus(s, "en_route"),
                    progressFromStatus(s, 80), store, eta.isEmpty() ? "On the way" : eta, etaMinutes);
        }
        if ("AWAITING_SHOPPING_APPROVAL".equals(s) || contains(t, "AWAITING_SHOPPING")) {
            return banner("SHOPPING_REVIEW", "Review substitutions",
                    "Your shopper needs a quick look at replacements.",
                    "orders", "time-sensitive", LiveAction.UPDATE, "shopping_review", 55,
                    "Review items", store, etaMinutes);
        }
        if (isOutForDelivery(t, s)) {
            String body = eta.isEmpty()
                    ? driver + " is on the way with your order."
                    : driver + " is about " + eta + " away.";
            return banner("OUT_FOR_DELIVERY", "On the way", body,
                    "orders", "active", LiveAction.UPDATE, "en_route", 80,
                    "On the way", eta.isEmpty() ? store : eta, etaMinutes);
        }
        if ("DRIVER_SHOPPING".equals(s) || contains(t, "DRIVER_SHOPPING")) {
            return silent("ORDER_STATUS_UPDATED", LiveAction.UPDATE, "shopping", 50,
                    store, "Shopping your list", etaMinutes);
        }
        if ("DELIVERY_ASSIGNED".equals(t) || "DRIVER_EN_ROUTE_TO_STORE".equals(s)
                || contains(t, "DRIVER_EN_ROUTE")) {
            return banner("DRIVER_ASSIGNED", driver + " is on the way to " + store,
                    "We’ll update you when they pick up.",
                    "orders", "active", LiveAction.UPDATE, "assigned", 45,
                    driver + " assigned", store, etaMinutes);
        }
        if ("READY_FOR_PICKUP".equals(s) || contains(t, "READY_FOR_PICKUP") || contains(t, "READY")) {
            if ("DELIVERY_EVENT".equals(t)) {
                return banner("ORDER_STATUS_UPDATED", "Ready for pickup",
                        store + " is packed. Finding a driver.",
                        "orders", "active", LiveAction.UPDATE, "ready", 35,
                        "Finding a driver", store, etaMinutes);
            }
            return banner("ORDER_STATUS_UPDATED", "Ready for pickup",
                    store + " has your order ready. Finding a driver.",
                    "orders", "active", LiveAction.UPDATE, "ready", 35,
                    "Finding a driver", store, etaMinutes);
        }
        if ("PREPARING".equals(s) || contains(t, "PREPARING")) {
            return banner("ORDER_STATUS_UPDATED", "Kitchen is preparing",
                    store + " started your order.",
                    "orders", "active", LiveAction.UPDATE, "preparing", 20,
                    "Preparing", store, etaMinutes);
        }
        if ("ORDER_CONFIRMED".equals(t) || "CREATED".equals(s) || "CONFIRMED".equals(s)
                || "ORDER_PAID".equals(t) || contains(t, "ORDER_STATUS_CREATED")
                || contains(t, "ORDER_STATUS_CONFIRMED")) {
            LiveAction live = "CONFIRMED".equals(s) || "CREATED".equals(s) || "ORDER_CONFIRMED".equals(t)
                    || "ORDER_PAID".equals(t) ? LiveAction.START : LiveAction.UPDATE;
            return banner("ORDER_CONFIRMED", "Order confirmed",
                    store + " got your order.",
                    "orders", "active", live, "confirmed", 10,
                    "Order confirmed", store, etaMinutes);
        }
        if ("ORDER_STATUS_UPDATED".equals(t) && s != null && !s.isBlank()) {
            return customer(s, s, storeName, driverName, etaMinutes);
        }
        return null;
    }

    public static Copy driverOffer(boolean softOffer, String storeName, Integer timeoutSeconds) {
        String store = storeLabel(storeName);
        if (softOffer) {
            String window = timeoutSeconds != null && timeoutSeconds > 0
                    ? " — accept within " + timeoutSeconds + "s"
                    : "";
            return banner("SOFT_OFFER", "Priority delivery offer",
                    store + window,
                    "deliveries", "time-sensitive", LiveAction.START, "offer", 5,
                    "New offer", store, timeoutSeconds);
        }
        return banner("NEW_DELIVERY", "New delivery available",
                store + " has an order ready for pickup",
                "deliveries", "time-sensitive", LiveAction.START, "offer", 5,
                "New delivery", store, timeoutSeconds);
    }

    public static Copy driverTrip(String eventType, String status, String storeName, Integer etaMinutes) {
        String t = upper(eventType);
        String s = upper(status);
        String store = storeLabel(storeName);
        String eta = etaLabel(etaMinutes);

        if (contains(t, "CANCEL") || "CANCELLED".equals(s)) {
            return banner("DELIVERY_CANCELLED", "Delivery cancelled",
                    "The order from " + store + " was cancelled.",
                    "deliveries", "active", LiveAction.END, "cancelled", 100,
                    "Cancelled", store, etaMinutes);
        }
        if (isDelivered(t, s)) {
            return banner("DELIVERY_COMPLETED", "Delivery complete",
                    "Nice work — order from " + store + " is delivered.",
                    "deliveries", "active", LiveAction.END, "delivered", 100,
                    "Delivered", store, etaMinutes);
        }
        if (contains(t, "ETA")) {
            return silent("LIVE_ETA", LiveAction.UPDATE, phaseFromStatus(s, "to_customer"),
                    progressFromStatus(s, 70), store, eta.isEmpty() ? "En route" : eta, etaMinutes);
        }
        if ("DELIVERY_ASSIGNED".equals(t) || contains(t, "ASSIGNED")) {
            return silent("DELIVERY_ASSIGNED", LiveAction.START, "to_store", 25,
                    store, "Head to pickup", etaMinutes);
        }
        if (isOutForDelivery(t, s) || "PICKED_UP".equals(s)) {
            return banner("DELIVERY_STATUS_UPDATED", "Head to the customer",
                    eta.isEmpty() ? "Order picked up from " + store : eta + " to dropoff",
                    "deliveries", "active", LiveAction.UPDATE, "to_customer", 70,
                    "To customer", eta.isEmpty() ? store : eta, etaMinutes);
        }
        if ("DRIVER_EN_ROUTE_TO_STORE".equals(s) || "DRIVER_SHOPPING".equals(s)
                || "READY_FOR_PICKUP".equals(s)) {
            return silent("DELIVERY_STATUS_UPDATED", LiveAction.UPDATE, "to_store", 40,
                    store, "To pickup", etaMinutes);
        }
        if (contains(t, "SOFT_OFFER") || contains(t, "EXPIRED")) {
            return silent("OFFER_EXPIRED", LiveAction.END, "expired", 0, store, "Offer ended", null);
        }
        return null;
    }

    public static Copy offerTakenDown(String storeName) {
        return silent("OFFER_REMOVED", LiveAction.END, "expired", 0, storeLabel(storeName), "Offer ended", null);
    }

    private static Copy banner(
            String type, String title, String body, String channel, String interruption,
            LiveAction live, String phase, int progress, String liveTitle, String liveSubtitle, Integer eta) {
        return new Copy(type, title, body, channel, interruption, true, live, phase, progress, liveTitle, liveSubtitle, eta);
    }

    private static Copy silent(
            String type, LiveAction live, String phase, int progress,
            String liveTitle, String liveSubtitle, Integer eta) {
        return new Copy(type, "", "", "orders", "passive", false, live, phase, progress, liveTitle, liveSubtitle, eta);
    }

    private static boolean isOutForDelivery(String eventType, String status) {
        return "PICKED_UP".equals(status) || "OUT_FOR_DELIVERY".equals(status)
                || contains(eventType, "PICKED_UP") || contains(eventType, "OUT_FOR_DELIVERY");
    }

    private static boolean isDelivered(String eventType, String status) {
        if ("DELIVERED".equals(status)) {
            return true;
        }
        return contains(eventType, "DELIVERED") && !contains(eventType, "DECLINED");
    }

    private static String phaseFromStatus(String status, String fallback) {
        return switch (status) {
            case "CREATED", "CONFIRMED" -> "confirmed";
            case "PREPARING" -> "preparing";
            case "READY_FOR_PICKUP" -> "ready";
            case "DRIVER_EN_ROUTE_TO_STORE" -> "assigned";
            case "DRIVER_SHOPPING", "AWAITING_SHOPPING_APPROVAL", "SHOPPING_COMPLETE" -> "shopping";
            case "PICKED_UP", "OUT_FOR_DELIVERY" -> "en_route";
            case "DELIVERED" -> "delivered";
            default -> fallback;
        };
    }

    private static int progressFromStatus(String status, int fallback) {
        return switch (status) {
            case "CREATED", "CONFIRMED" -> 10;
            case "PREPARING" -> 20;
            case "READY_FOR_PICKUP" -> 35;
            case "DRIVER_EN_ROUTE_TO_STORE" -> 45;
            case "DRIVER_SHOPPING", "AWAITING_SHOPPING_APPROVAL" -> 55;
            case "PICKED_UP", "OUT_FOR_DELIVERY" -> 80;
            case "DELIVERED" -> 100;
            default -> fallback;
        };
    }

    private static String etaLabel(Integer etaMinutes) {
        if (etaMinutes == null || etaMinutes < 0) {
            return "";
        }
        if (etaMinutes <= 1) {
            return "About a minute";
        }
        return etaMinutes + " min";
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && needle != null && haystack.contains(needle);
    }
}
