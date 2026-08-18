package com.umameats.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umameats.messaging.TraceContext;
import com.umameats.model.Order;
import com.umameats.notify.CustomerOrderPushNotifier;
import com.umameats.notify.KafkaPayload;
import com.umameats.notify.NotificationCatalog;
import com.umameats.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns customer.notifications + delivery lifecycle Kafka into Expo banners
 * and live-activity updates. Dedicated group so payment consumption is untouched.
 * auto.offset.reset=latest avoids replaying history onto every device.
 */
@Slf4j
@Service
public class CustomerNotificationConsumer {

    public static final String CONSUMER_GROUP = "order-api-customer-push-group";

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final CustomerOrderPushNotifier customerOrderPushNotifier;
    private final ConcurrentHashMap<String, EtaTick> lastEta = new ConcurrentHashMap<>();

    public CustomerNotificationConsumer(
            ObjectMapper objectMapper,
            OrderRepository orderRepository,
            CustomerOrderPushNotifier customerOrderPushNotifier) {
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.customerOrderPushNotifier = customerOrderPushNotifier;
    }

    @KafkaListener(
            topics = {
                    "umameats.customer.notifications",
                    "umameats.delivery.events",
                    "umameats.delivery.status",
                    "umameats.delivery.eta",
                    "umameats.order.status"
            },
            groupId = CONSUMER_GROUP,
            properties = "auto.offset.reset:latest")
    public void consume(
            @Payload String eventJson,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic) {

        Map<String, Object> root;
        try {
            root = objectMapper.readValue(eventJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid customer notification JSON", e);
        }

        Map<String, Object> payload = KafkaPayload.unwrap(root);
        String orderId = KafkaPayload.stringVal(payload.get("orderId"));
        if (orderId == null) {
            orderId = KafkaPayload.stringVal(payload.get("deliveryId"));
        }
        if (orderId == null) {
            orderId = key;
        }
        if (orderId == null) {
            return;
        }
        TraceContext.setOrderId(orderId);

        String eventType = firstNonBlank(
                KafkaPayload.stringVal(payload.get("eventType")),
                KafkaPayload.stringVal(payload.get("type")),
                KafkaPayload.stringVal(root.get("eventType")));
        String status = firstNonBlank(
                KafkaPayload.stringVal(payload.get("newStatus")),
                KafkaPayload.stringVal(payload.get("status")));
        Integer etaMinutes = firstInt(
                payload.get("remainingEtaMinutes"),
                payload.get("etaMinutes"),
                payload.get("deliveryEtaMinutes"),
                payload.get("eta"),
                nested(payload, "metrics", "remainingEtaMinutes"),
                nested(payload, "metrics", "deliveryEtaMinutes"));
        Long arrivesAtMs = KafkaPayload.longVal(payload.get("arrivesAtMs"));
        if (arrivesAtMs == null) {
            arrivesAtMs = KafkaPayload.longVal(nested(payload, "metrics", "arrivesAtMs"));
        }

        if (isEtaTopic(topic, eventType) && !shouldPublishEta(orderId, etaMinutes)) {
            return;
        }

        Order order = orderRepository.findById(orderId, null).orElse(null);
        if (order == null) {
            log.info("push.skipped_no_order orderId={} eventType={}", orderId, eventType);
            return;
        }
        if (status == null && order.getStatus() != null) {
            status = order.getStatus().name();
        }

        if (isEtaTopic(topic, eventType) && arrivesAtMs != null) {
            try {
                orderRepository.updateLastArrivesAtMs(orderId, arrivesAtMs);
            } catch (Exception e) {
                log.warn("eta.persist_failed orderId={} err={}", orderId, e.getMessage());
            }
        }

        NotificationCatalog.Copy copy = NotificationCatalog.customer(
                eventType,
                status,
                order.getStoreName(),
                order.getAssignedDriverName(),
                etaMinutes);
        if (copy == null) {
            return;
        }
        customerOrderPushNotifier.notifyCustomer(order, copy);
    }

    private boolean shouldPublishEta(String orderId, Integer etaMinutes) {
        if (etaMinutes == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        EtaTick previous = lastEta.get(orderId);
        if (previous != null
                && now - previous.atMs < 60_000L
                && Math.abs(previous.minutes - etaMinutes) < 2) {
            return false;
        }
        lastEta.put(orderId, new EtaTick(etaMinutes, now));
        return true;
    }

    private static boolean isEtaTopic(String topic, String eventType) {
        return (topic != null && topic.contains(".eta"))
                || (eventType != null && eventType.toUpperCase().contains("ETA"));
    }

    @SuppressWarnings("unchecked")
    private static Object nested(Map<String, Object> payload, String a, String b) {
        Object first = payload.get(a);
        if (first instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).get(b);
        }
        return null;
    }

    private static Integer firstInt(Object... values) {
        for (Object value : values) {
            Integer parsed = KafkaPayload.intVal(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record EtaTick(int minutes, long atMs) {
    }
}
