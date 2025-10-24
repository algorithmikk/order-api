package com.umameats.kafka;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umameats.model.OrderStatus;
import com.umameats.repository.OrderRepository;
import com.umameats.model.Order;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PaymentEventConsumer {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventProducer orderEventProducer;

    @KafkaListener(topics = "umameats.payment.events", groupId = "order-api-group")
    public void consumePaymentEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp) {
        
        try {
            // Parse the payment event
            Map<String, Object> paymentEvent = objectMapper.readValue(eventJson, new TypeReference<Map<String, Object>>() {});
            
            String eventType = (String) paymentEvent.get("eventType");
            String orderId = (String) paymentEvent.get("orderId");
            String customerId = (String) paymentEvent.get("customerId");
            
            log.info("Received payment event: type={}, orderId={}, customerId={}", eventType, orderId, customerId);
            
            // Handle different payment event types
            if ("PAYMENT_SUCCESS".equals(eventType)) {
                handlePaymentSuccess(orderId, customerId, paymentEvent);
            } else if ("PAYMENT_FAILED".equals(eventType)) {
                handlePaymentFailed(orderId, customerId, paymentEvent);
            }
            
        } catch (Exception e) {
            log.error("Error processing payment event: {}", eventJson, e);
        }
    }

    private void handlePaymentSuccess(String orderId, String customerId, Map<String, Object> paymentEvent) {
        try {
            // Fetch the order
            Order order = orderRepository.findById(orderId, customerId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
            
            // Update order status to CREATED
            order.setStatus(OrderStatus.CREATED);
            orderRepository.save(order);
            
            log.info("Order status updated to CREATED after payment success: orderId={}", orderId);
            
            // Publish order status change event
            orderEventProducer.publishOrderStatusChange(orderId, "CREATED", order);
            
            // Publish store notification (new order)
            Map<String, Object> storeNotification = Map.of(
                "eventType", "NEW_ORDER",
                "orderId", orderId,
                "storeId", order.getStoreId(),
                "customerId", customerId,
                "totalAmount", order.getTotalAmount(),
                "items", order.getItems(),
                "timestamp", System.currentTimeMillis()
            );
            orderEventProducer.publishStoreNotification(order.getStoreId(), orderId, storeNotification);
            
            // Publish customer notification (order confirmed)
            Map<String, Object> customerNotification = Map.of(
                "eventType", "ORDER_CONFIRMED",
                "orderId", orderId,
                "customerId", customerId,
                "status", "CREATED",
                "timestamp", System.currentTimeMillis()
            );
            orderEventProducer.publishCustomerNotification(customerId, orderId, customerNotification);
            
        } catch (Exception e) {
            log.error("Error handling payment success: orderId={}", orderId, e);
        }
    }

    private void handlePaymentFailed(String orderId, String customerId, Map<String, Object> paymentEvent) {
        try {
            // Fetch the order
            Order order = orderRepository.findById(orderId, customerId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
            
            // Update order status to PAYMENT_FAILED
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            orderRepository.save(order);
            
            log.info("Order status updated to PAYMENT_FAILED: orderId={}", orderId);
            
            // Publish order status change event
            orderEventProducer.publishOrderStatusChange(orderId, "PAYMENT_FAILED", order);
            
            // Publish customer notification (payment failed)
            Map<String, Object> customerNotification = Map.of(
                "eventType", "PAYMENT_FAILED",
                "orderId", orderId,
                "customerId", customerId,
                "status", "PAYMENT_FAILED",
                "timestamp", System.currentTimeMillis()
            );
            orderEventProducer.publishCustomerNotification(customerId, orderId, customerNotification);
            
        } catch (Exception e) {
            log.error("Error handling payment failed: orderId={}", orderId, e);
        }
    }
}

