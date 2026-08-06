package com.umameats.kafka;

import com.umameats.messaging.KafkaProducerSupport;
import com.umameats.messaging.outbox.OutboxWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Publishes order lifecycle events. Uses transactional outbox when enabled.
 */
@Slf4j
@Service
public class OrderEventProducer {

    public static final String TOPIC_ORDER_EVENTS = "umameats.order.events";
    public static final String TOPIC_ORDER_STATUS = "umameats.order.status";
    public static final String TOPIC_STORE_NOTIFICATIONS = "umameats.store.notifications";
    public static final String TOPIC_CUSTOMER_NOTIFICATIONS = "umameats.customer.notifications";

    private final KafkaProducerSupport kafkaProducerSupport;
    private final OutboxWriter outboxWriter;

    public OrderEventProducer(
            KafkaProducerSupport kafkaProducerSupport,
            @Autowired(required = false) OutboxWriter outboxWriter) {
        this.kafkaProducerSupport = kafkaProducerSupport;
        this.outboxWriter = outboxWriter;
    }

    public void publishOrderCreated(String orderId, Object orderData) {
        publish(TOPIC_ORDER_EVENTS, orderId, orderData, "ORDER_CREATED");
    }

    public void publishOrderPaid(String orderId, Object orderData) {
        publish(TOPIC_ORDER_EVENTS, orderId, orderData, "ORDER_PAID");
    }

    public void publishOrderStatusChange(String orderId, String status, Object orderData) {
        publish(TOPIC_ORDER_STATUS, orderId, orderData, "ORDER_STATUS_" + status);
    }

    public void publishStoreNotification(String storeId, String orderId, Object notificationData) {
        publish(TOPIC_STORE_NOTIFICATIONS, storeId, notificationData, "STORE_NOTIFICATION");
    }

    public void publishCustomerNotification(String customerId, String orderId, Object notificationData) {
        publish(TOPIC_CUSTOMER_NOTIFICATIONS, customerId, notificationData, "CUSTOMER_NOTIFICATION");
    }

    private void publish(String topic, String key, Object payload, String eventType) {
        if (outboxWriter != null) {
            outboxWriter.enqueue(topic, key, payload, eventType);
            return;
        }
        kafkaProducerSupport.sendJson(topic, key, payload, eventType);
    }
}
