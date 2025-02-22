package com.umameats.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.umameats.client.PaymentApiClient;
import com.umameats.model.Order;
import com.umameats.model.OrderStatus;
import com.umameats.model.TransactionRequest;
import com.umameats.repository.OrderRepository;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentApiClient paymentApiClient;

    public OrderService(OrderRepository orderRepository, PaymentApiClient paymentApiClient) {
        this.orderRepository = orderRepository;
        this.paymentApiClient = paymentApiClient;
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
                    savedOrder.setStatus(OrderStatus.CREATED);
                    orderRepository.save(savedOrder);
                },
                error -> {
                    savedOrder.setStatus(OrderStatus.PAYMENT_FAILED);
                    orderRepository.save(savedOrder);
                    throw new RuntimeException("Payment creation failed", error);
                }
            );

        return savedOrder;
    }

    public Order getOrder(String orderId, String customerId) {
        return orderRepository.findById(orderId, customerId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    public List<Order> getCustomerOrders(String customerId) {
        return orderRepository.findByCustomerId(customerId);  // Fixed method call
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