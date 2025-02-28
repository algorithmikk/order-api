package com.umameats.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.umameats.client.EventApiClient;
import com.umameats.client.PaymentApiClient;

import com.umameats.model.DeliveryAddress;
import com.umameats.model.DeliveryEvent;
import com.umameats.model.EventRequest;
import com.umameats.model.Order;
import com.umameats.model.OrderItem;
import com.umameats.model.OrderStatus;
import com.umameats.model.TransactionRequest;
import com.umameats.repository.OrderRepository;

import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentApiClient paymentApiClient;
    private final EventApiClient eventApiClient;

    public OrderService(OrderRepository orderRepository, 
                        PaymentApiClient paymentApiClient,
                        EventApiClient eventApiClient) {
        this.orderRepository = orderRepository;
        this.paymentApiClient = paymentApiClient;
        this.eventApiClient = eventApiClient;
    }

    public Order createOrder(Order order) {
        // Create order first
        order.setOrderId(UUID.randomUUID().toString());
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        Order savedOrder = orderRepository.save(order);

        // Create payment transaction with full payment details
        TransactionRequest transactionRequest = TransactionRequest.builder()
            .orderId(savedOrder.getOrderId())
            .customerId(savedOrder.getCustomerId())
            .storeId(savedOrder.getStoreId())
            .amount(savedOrder.getTotalAmount())
            .paymentMethodId(savedOrder.getPaymentMethodId())
            .currency(savedOrder.getBillingDetails().getCurrency())
            .billingDetails(savedOrder.getBillingDetails())
            .build();

        // Call payment API
        paymentApiClient.createTransaction(transactionRequest, order.getCustomerId())
            .subscribe(
                transactionResponse -> {
                    // Update order status on successful payment
                    savedOrder.setStatus(OrderStatus.CREATED);
                    Order updatedOrder = orderRepository.save(savedOrder);
                    
                    // After successful payment, create and send delivery event
                    createAndSendDeliveryEvent(updatedOrder);
                },
                error -> {
                    savedOrder.setStatus(OrderStatus.PAYMENT_FAILED);
                    orderRepository.save(savedOrder);
                    log.error("Payment creation failed for order: {}", savedOrder.getOrderId(), error);
                    throw new RuntimeException("Payment creation failed", error);
                }
            );

        return savedOrder;
    }
    
    /**
     * Creates and sends a delivery event for the given order
     * 
     * @param order The order to create a delivery event for
     */
    private void createAndSendDeliveryEvent(Order order) {
        try {
            // Create the delivery event
            DeliveryEvent deliveryEvent = createDeliveryEventFromOrder(order);
            
            // Create the event request
            EventRequest eventRequest = EventRequest.builder()
                .eventType("DELIVERY_EVENT")
                .eventSource("order-service")
                .payload(deliveryEvent)
                .timestamp(LocalDateTime.now())
                .build();
            
            // Send to the event API
            eventApiClient.createDeliveryEvent(order.getOrderId(), eventRequest)
                .subscribe(
                    response -> {
                        log.info("Delivery event created successfully for order: {}, eventId: {}", 
                                order.getOrderId(), response.getEventId());
                        
                        // Update order status to ready for pickup
                        order.setStatus(OrderStatus.READY_FOR_PICKUP);
                        orderRepository.save(order);
                    },
                    error -> {
                        log.error("Failed to create delivery event for order: {}", order.getOrderId(), error);
                    }
                );
        } catch (Exception e) {
            log.error("Error creating delivery event for order: {}", order.getOrderId(), e);
        }
    }
    
    /**
     * Creates a delivery event object from an order
     * 
     * @param order The order to create a delivery event from
     * @return A delivery event populated with order data
     */
    private DeliveryEvent createDeliveryEventFromOrder(Order order) {
        // Generate a delivery ID
        String deliveryId = UUID.randomUUID().toString();
        
        // Convert delivery address to location
        DeliveryEvent.Location deliveryLocation = convertAddressToLocation(order.getDeliveryAddress());
        
        // Create order items for the payload
        List<DeliveryEvent.OrderItem> orderItems = convertOrderItems(order.getItems());
        
        // Create order info
        DeliveryEvent.OrderInfo orderInfo = DeliveryEvent.OrderInfo.builder()
                .orderId(order.getOrderId())
                .items(orderItems)
                .orderTotal(order.getTotalAmount().doubleValue() / 100) // Convert cents to dollars
                .isPrepaid(true)
                .paymentMethod(order.getPaymentMethod())
                .build();
        
        // Build and return the delivery event
        return DeliveryEvent.builder()
                .deliveryId(deliveryId)
                .orderId(order.getOrderId())
                .customerId(order.getCustomerId())
                .restaurantId(order.getStoreId())
                .status(DeliveryEvent.DeliveryStatus.PENDING_ASSIGNMENT)
                .createdAt(LocalDateTime.now())
                .deliveryLocation(deliveryLocation)
                .specialInstructions(order.getSpecialInstructions())
                .orderInfo(orderInfo)
                .build();
    }
    
    private List<DeliveryEvent.OrderItem> convertOrderItems(List<OrderItem> items) {
        if (items == null) {
            return List.of();
        }
        
        return items.stream()
                .<DeliveryEvent.OrderItem>map(item -> DeliveryEvent.OrderItem.builder()
                        .itemId(item.getItemId())
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .specialInstructions(item.getSpecialInstructions())
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * Converts a delivery address to a location object
     */
    private DeliveryEvent.Location convertAddressToLocation(DeliveryAddress address) {
        if (address == null) {
            return null;
        }
        
        return DeliveryEvent.Location.builder()
                .address(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getZipCode())
                .latitude(0.00)
                .longitude(0.00)
                .build();
    }

    public Order getOrder(String orderId, String customerId) {
        return orderRepository.findById(orderId, customerId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    public List<Order> getCustomerOrders(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public List<Order> getStoreOrders(String storeId, String status) {
        return orderRepository.findByStoreIdAndStatus(storeId, status);
    }

    public Order updateOrderStatus(String orderId, String customerId, OrderStatus newStatus) {
        Order order = getOrder(orderId, customerId);
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }
}