package com.umameats.notify;

import com.umameats.chat.push.ExpoPushSender;
import com.umameats.chat.push.PushTokenDirectory;
import com.umameats.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends a catalog Copy as an Expo banner plus an iOS Live Activity APNs update.
 */
@Slf4j
@Component
public class CustomerOrderPushNotifier {

    public static final String CUSTOMER_BUNDLE_ID = "com.umameats.customer";
    public static final String ATTRIBUTES_TYPE = "UmaOrderAttributes";

    private final PushTokenDirectory pushTokenDirectory;
    private final ExpoPushSender expoPushSender;
    private final LiveActivityApnsClient liveActivityApnsClient;
    private final NotificationDedupe notificationDedupe;

    public CustomerOrderPushNotifier(
            PushTokenDirectory pushTokenDirectory,
            ExpoPushSender expoPushSender,
            LiveActivityApnsClient liveActivityApnsClient,
            NotificationDedupe notificationDedupe) {
        this.pushTokenDirectory = pushTokenDirectory;
        this.expoPushSender = expoPushSender;
        this.liveActivityApnsClient = liveActivityApnsClient;
        this.notificationDedupe = notificationDedupe;
    }

    public void notifyCustomer(Order order, NotificationCatalog.Copy copy) {
        if (order == null || copy == null || order.getCustomerId() == null) {
            return;
        }

        if (!notificationDedupe.shouldSend(order.getCustomerId(), order.getOrderId(), copy.type)) {
            log.info("push.skipped_dedupe customerId={} orderId={} type={}",
                    order.getCustomerId(), order.getOrderId(), copy.type);
            return;
        }

        PushTokenDirectory.DevicePush device = pushTokenDirectory.findCustomer(order.getCustomerId()).orElse(null);
        if (device == null || !device.hasExpoToken()) {
            log.info("push.skipped_no_token customerId={} orderId={} type={}",
                    order.getCustomerId(), order.getOrderId(), copy.type);
            return;
        }
        if (!device.orderUpdates() && copy.sendBanner) {
            log.info("push.skipped_prefs customerId={} orderId={} type={}",
                    order.getCustomerId(), order.getOrderId(), copy.type);
            return;
        }

        Map<String, Object> data = liveData(order, copy);
        expoPushSender.sendCopy(
                device.expoPushToken(),
                copy,
                data,
                "order-" + order.getOrderId());

        sendLiveActivity(device, order, copy);
    }

    private void sendLiveActivity(
            PushTokenDirectory.DevicePush device,
            Order order,
            NotificationCatalog.Copy copy) {
        if (copy.liveAction == NotificationCatalog.LiveAction.NONE) {
            return;
        }
        Map<String, Object> contentState = contentState(order, copy);
        Map<String, Object> attributes = Map.of(
                "orderId", order.getOrderId(),
                "storeName", NotificationCatalog.storeLabel(order.getStoreName()));

        if (copy.liveAction == NotificationCatalog.LiveAction.START) {
            String token = firstNonBlank(device.liveActivityPushToStartToken(), device.liveActivityUpdateToken());
            liveActivityApnsClient.start(
                    token,
                    CUSTOMER_BUNDLE_ID,
                    ATTRIBUTES_TYPE,
                    attributes,
                    contentState,
                    copy.liveTitle,
                    copy.liveSubtitle);
            return;
        }

        String updateToken = device.liveActivityUpdateToken();
        if (updateToken == null && order.getOrderId().equals(device.liveActivityOrderId())) {
            updateToken = device.liveActivityUpdateToken();
        }
        if (copy.liveAction == NotificationCatalog.LiveAction.END) {
            liveActivityApnsClient.end(updateToken, CUSTOMER_BUNDLE_ID, contentState);
        } else {
            liveActivityApnsClient.update(updateToken, CUSTOMER_BUNDLE_ID, contentState);
        }
    }

    public static Map<String, Object> liveData(Order order, NotificationCatalog.Copy copy) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", copy.type);
        data.put("orderId", order.getOrderId());
        data.put("deliveryId", order.getOrderId());
        data.put("phase", copy.phase);
        data.put("liveAction", copy.liveAction.name());
        data.put("progressPercent", copy.progressPercent);
        data.put("liveTitle", copy.liveTitle);
        data.put("liveSubtitle", copy.liveSubtitle);
        data.put("audience", "customer");
        if (copy.etaMinutes != null) {
            data.put("etaMinutes", copy.etaMinutes);
        }
        if (order.getStoreName() != null) {
            data.put("storeName", order.getStoreName());
        }
        if (order.getAssignedDriverName() != null) {
            data.put("driverName", NotificationCatalog.firstName(order.getAssignedDriverName(), "Driver"));
        }
        return data;
    }

    private static Map<String, Object> contentState(Order order, NotificationCatalog.Copy copy) {
        Map<String, Object> state = new HashMap<>();
        state.put("phase", copy.phase);
        state.put("progressPercent", copy.progressPercent);
        state.put("title", copy.liveTitle);
        state.put("subtitle", copy.liveSubtitle);
        state.put("storeName", NotificationCatalog.storeLabel(order.getStoreName()));
        state.put("driverName", NotificationCatalog.firstName(order.getAssignedDriverName(), ""));
        if (copy.etaMinutes != null) {
            state.put("etaMinutes", copy.etaMinutes);
        }
        return state;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
