package com.umameats.kafka;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umameats.messaging.TraceContext;
import com.umameats.messaging.consumer.IdempotentEventProcessor;
import com.umameats.model.Order;
import com.umameats.model.OrderStatus;
import com.umameats.repository.OrderRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PaymentEventConsumer {

    public static final String CONSUMER_GROUP = "order-api-group";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventProducer orderEventProducer;

    @Autowired
    private com.umameats.service.RestaurantNotificationService restaurantNotificationService;

    @Autowired
    private com.umameats.service.WhatsAppNotificationService whatsAppNotificationService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.umameats.service.OrderService orderService;

    @Autowired(required = false)
    private IdempotentEventProcessor idempotentEventProcessor;

    @KafkaListener(topics = "umameats.payment.events", groupId = CONSUMER_GROUP)
    public void consumePaymentEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp) {

        Map<String, Object> paymentEvent;
        try {
            paymentEvent = objectMapper.readValue(eventJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Poison payment event (deserialization failed) key={}", key, e);
            throw new IllegalArgumentException("Invalid payment event JSON", e);
        }

        String eventType = (String) paymentEvent.get("eventType");
        String orderId = (String) paymentEvent.get("orderId");
        String customerId = (String) paymentEvent.get("customerId");
        TraceContext.setOrderId(orderId);

        log.info("Received payment event: type={}, orderId={}, customerId={}", eventType, orderId, customerId);

        Runnable work = () -> {
            if ("PAYMENT_SUCCESS".equals(eventType)) {
                handlePaymentSuccess(orderId, customerId, paymentEvent);
            } else if ("PAYMENT_FAILED".equals(eventType)) {
                handlePaymentFailed(orderId, customerId, paymentEvent);
            }
        };

        if (idempotentEventProcessor != null) {
            idempotentEventProcessor.process(CONSUMER_GROUP, "umameats.payment.events", paymentEvent, orderId, payload -> work.run());
        } else {
            work.run();
        }
    }

    private void handlePaymentSuccess(String orderId, String customerId, Map<String, Object> paymentEvent) {
        Order order = orderRepository.findById(orderId, customerId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.CREATED
                || order.getStatus() == OrderStatus.CONFIRMED
                || order.getStatus() == OrderStatus.PREPARING
                || order.getStatus() == OrderStatus.READY_FOR_PICKUP
                || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY
                || order.getStatus() == OrderStatus.DELIVERED) {
            log.info("Idempotent skip: order already past payment success orderId={} status={}", orderId, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.CREATED);
        orderRepository.save(order);
        log.info("Order status updated to CREATED after payment success: orderId={}", orderId);

        orderEventProducer.publishOrderStatusChange(orderId, "CREATED", order);

        String customerName = order.getBillingDetails() != null ? order.getBillingDetails().getName() : null;
        if (customerName == null && order.getDeliveryAddress() != null) {
            customerName = order.getDeliveryAddress().getFullName();
        }
        String customerPhone = order.getBillingDetails() != null ? order.getBillingDetails().getPhone() : null;
        if (customerPhone == null && order.getDeliveryAddress() != null) {
            customerPhone = order.getDeliveryAddress().getPhone();
        }

        Map<String, Object> orderPaid = new HashMap<>();
        orderPaid.put("eventType", "ORDER_PAID");
        orderPaid.put("orderId", orderId);
        orderPaid.put("restaurantId", order.getStoreId());
        orderPaid.put("storeId", order.getStoreId());
        orderPaid.put("customerId", customerId);
        orderPaid.put("customerName", customerName);
        orderPaid.put("customerPhone", customerPhone);
        orderPaid.put("items", order.getItems());
        orderPaid.put("subtotal", order.getSubtotal());
        orderPaid.put("tax", order.getTaxAmount());
        orderPaid.put("deliveryFee", order.getDeliveryFee());
        orderPaid.put("total", order.getTotalAmount());
        orderPaid.put("totalAmount", order.getTotalAmount());
        orderPaid.put("paymentMethod", paymentEvent.getOrDefault("paymentMethod", order.getPaymentMethod()));
        orderPaid.put("specialInstructions", order.getSpecialInstructions());
        orderPaid.put("paidAt", System.currentTimeMillis());
        orderEventProducer.publishOrderPaid(orderId, orderPaid);

        Map<String, Object> storeNotification = Map.of(
                "eventType", "NEW_ORDER",
                "orderId", orderId,
                "storeId", order.getStoreId(),
                "customerId", customerId,
                "totalAmount", order.getTotalAmount(),
                "items", order.getItems(),
                "timestamp", System.currentTimeMillis());
        orderEventProducer.publishStoreNotification(order.getStoreId(), orderId, storeNotification);

        Map<String, Object> customerNotification = Map.of(
                "eventType", "ORDER_CONFIRMED",
                "orderId", orderId,
                "customerId", customerId,
                "status", "CREATED",
                "timestamp", System.currentTimeMillis());
        orderEventProducer.publishCustomerNotification(customerId, orderId, customerNotification);

        restaurantNotificationService.sendNewOrderNotification(order);
        whatsAppNotificationService.sendNewOrderWhatsApp(order);
        orderService.dispatchDriverShopsIfNeeded(order);
    }

    private void handlePaymentFailed(String orderId, String customerId, Map<String, Object> paymentEvent) {
        Order order = orderRepository.findById(orderId, customerId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            log.info("Idempotent skip: order already PAYMENT_FAILED orderId={}", orderId);
            return;
        }

        order.setStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);
        log.info("Order status updated to PAYMENT_FAILED: orderId={}", orderId);

        orderEventProducer.publishOrderStatusChange(orderId, "PAYMENT_FAILED", order);

        Map<String, Object> customerNotification = Map.of(
                "eventType", "PAYMENT_FAILED",
                "orderId", orderId,
                "customerId", customerId,
                "status", "PAYMENT_FAILED",
                "timestamp", System.currentTimeMillis());
        orderEventProducer.publishCustomerNotification(customerId, orderId, customerNotification);
    }
}
