package com.umameats.chat.push;

import com.umameats.chat.model.ChatMessage;
import com.umameats.chat.model.ChatRole;
import com.umameats.chat.service.DeliveryChatNotifier;
import com.umameats.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Notifies the other party about a new delivery-chat message.
 *
 * <p>Runs asynchronously so the sender's POST returns as soon as the message is
 * stored; two Dynamo reads and an Expo round trip have no business sitting in
 * that request.
 */
@Slf4j
@Component
public class ChatPushNotifier implements DeliveryChatNotifier {

    private static final String CHANNEL_ID = "chat";
    private static final int PREVIEW_LENGTH = 120;

    private final PushTokenDirectory pushTokenDirectory;
    private final ExpoPushSender expoPushSender;

    public ChatPushNotifier(PushTokenDirectory pushTokenDirectory, ExpoPushSender expoPushSender) {
        this.pushTokenDirectory = pushTokenDirectory;
        this.expoPushSender = expoPushSender;
    }

    @Async
    @Override
    public void notifyCounterpart(Order order, ChatMessage message) {
        try {
            boolean fromDriver = ChatRole.DRIVER.name().equals(message.getSenderRole());

            Optional<String> token;
            if (fromDriver) {
                var device = pushTokenDirectory.findCustomer(order.getCustomerId()).orElse(null);
                if (device == null || !device.hasExpoToken() || !device.driverMessages()) {
                    log.debug("No chat push for customer on order {}", order.getOrderId());
                    return;
                }
                token = Optional.of(device.expoPushToken());
            } else {
                token = driverToken(order);
            }

            if (token.isEmpty()) {
                log.debug("No push token for the counterpart on order {}", order.getOrderId());
                return;
            }

            String title = fromDriver
                    ? senderName(order.getAssignedDriverName(), "Your driver")
                    : senderName(customerName(order), "Your customer");

            // The type and orderId are what the apps' notification response
            // listeners use to deep link straight into this conversation.
            expoPushSender.send(
                    token.get(),
                    title,
                    preview(message.getBody()),
                    CHANNEL_ID,
                    Map.of(
                            "type", "DELIVERY_CHAT",
                            "orderId", order.getOrderId(),
                            "deliveryId", order.getOrderId()));
        } catch (Exception e) {
            log.warn("Chat push failed for order {}: {}", order.getOrderId(), e.getMessage());
        }
    }

    /** Notifies a user that a human support agent replied to their thread. */
    @Async
    public void notifySupportReply(String principalId, ChatRole role, String threadId, String body) {
        Optional<String> token = role == ChatRole.DRIVER
                ? pushTokenDirectory.findDriverToken(principalId)
                : pushTokenDirectory.findCustomerToken(principalId);

        token.ifPresent(value -> expoPushSender.send(
                value,
                "UmaMeats Support",
                preview(body),
                CHANNEL_ID,
                Map.of("type", "SUPPORT_REPLY", "threadId", threadId)));
    }

    private Optional<String> driverToken(Order order) {
        Optional<String> token = pushTokenDirectory.findDriverToken(order.getAssignedDriverId());
        return token.isPresent() ? token : pushTokenDirectory.findDriverToken(order.getDriverId());
    }

    private String customerName(Order order) {
        return order.getDeliveryAddress() != null ? order.getDeliveryAddress().getFullName() : null;
    }

    private String senderName(String name, String fallback) {
        return name != null && !name.isBlank() ? name : fallback;
    }

    private String preview(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= PREVIEW_LENGTH ? body : body.substring(0, PREVIEW_LENGTH) + "…";
    }
}
