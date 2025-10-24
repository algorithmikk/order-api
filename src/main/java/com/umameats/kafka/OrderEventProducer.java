package com.umameats.kafka;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderEventProducer {

    private static final String TOPIC_ORDER_EVENTS = "umameats.order.events";
    private static final String TOPIC_ORDER_STATUS = "umameats.order.status";
    private static final String TOPIC_STORE_NOTIFICATIONS = "umameats.store.notifications";
    private static final String TOPIC_CUSTOMER_NOTIFICATIONS = "umameats.customer.notifications";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Publish order created event
     */
    public void publishOrderCreated(String orderId, Object orderData) {
        try {
            String eventJson = objectMapper.writeValueAsString(orderData);
            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(TOPIC_ORDER_EVENTS, orderId, eventJson);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Published ORDER_CREATED event: orderId={}, partition={}, offset={}", 
                        orderId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to publish ORDER_CREATED event: orderId={}", orderId, ex);
                }
            });
        } catch (Exception e) {
            log.error("Error serializing order created event: orderId={}", orderId, e);
        }
    }

    /**
     * Publish order status change event
     */
    public void publishOrderStatusChange(String orderId, String status, Object orderData) {
        try {
            String eventJson = objectMapper.writeValueAsString(orderData);
            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(TOPIC_ORDER_STATUS, orderId, eventJson);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Published ORDER_STATUS_CHANGE event: orderId={}, status={}, partition={}, offset={}", 
                        orderId,
                        status,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to publish ORDER_STATUS_CHANGE event: orderId={}, status={}", orderId, status, ex);
                }
            });
        } catch (Exception e) {
            log.error("Error serializing order status change event: orderId={}, status={}", orderId, status, e);
        }
    }

    /**
     * Publish store notification (new order alert)
     */
    public void publishStoreNotification(String storeId, String orderId, Object notificationData) {
        try {
            String eventJson = objectMapper.writeValueAsString(notificationData);
            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(TOPIC_STORE_NOTIFICATIONS, storeId, eventJson);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Published STORE_NOTIFICATION: storeId={}, orderId={}, partition={}, offset={}", 
                        storeId,
                        orderId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to publish STORE_NOTIFICATION: storeId={}, orderId={}", storeId, orderId, ex);
                }
            });
        } catch (Exception e) {
            log.error("Error serializing store notification: storeId={}, orderId={}", storeId, orderId, e);
        }
    }

    /**
     * Publish customer notification (order update)
     */
    public void publishCustomerNotification(String customerId, String orderId, Object notificationData) {
        try {
            String eventJson = objectMapper.writeValueAsString(notificationData);
            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(TOPIC_CUSTOMER_NOTIFICATIONS, customerId, eventJson);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Published CUSTOMER_NOTIFICATION: customerId={}, orderId={}, partition={}, offset={}", 
                        customerId,
                        orderId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to publish CUSTOMER_NOTIFICATION: customerId={}, orderId={}", customerId, orderId, ex);
                }
            });
        } catch (Exception e) {
            log.error("Error serializing customer notification: customerId={}, orderId={}", customerId, orderId, e);
        }
    }
}

