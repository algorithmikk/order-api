package com.umameats.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.umameats.model.Order;
import com.umameats.model.OrderStatus;
import com.umameats.service.OrderService;

@RestController
@RequestMapping("/api/v1/orders")
@CrossOrigin(
    origins = {
        "http://localhost:3000",
        "https://www.umameats.com",
        "https://umameats.com",
        "https://umameats-customer-app.vercel.app",
        "https://umameats-landing-saas.vercel.app",
        "https://umameats-driver-app.vercel.app"
    },
    allowCredentials = "true",
    allowedHeaders = {"Authorization", "Content-Type", "Accept", "X-Customer-Id", "X-Store-Id", "X-Driver-Id"},
    methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS}
)
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(
            @RequestBody Order order,
            @RequestHeader("X-Customer-Id") String customerId
    ) {
        order.setCustomerId(customerId);
        return ResponseEntity.ok(orderService.createOrder(order));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(
            @PathVariable String orderId,
            @RequestHeader("X-Customer-Id") String customerId
    ) {
        try {
            Order order = orderService.getOrder(orderId, customerId);
            // Verify the requesting user owns this order
            if (!order.getCustomerId().equals(customerId)) {
                return ResponseEntity.status(403)
                    .body(Map.of("error", "Access denied"));
            }
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            return ResponseEntity.status(404)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getCustomerOrders(
            @PathVariable String customerId,
            @RequestHeader("X-Customer-Id") String requestingCustomerId
    ) {
        try {
            // Verify the requesting user matches the customer ID
            if (!customerId.equals(requestingCustomerId)) {
                return ResponseEntity.status(403)
                    .body(Map.of("error", "Access denied"));
            }
            List<Order> orders = orderService.getCustomerOrders(customerId);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.status(400)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<Order>> getStoreOrders(
            @PathVariable String storeId,
            @RequestParam(required = false) String status
    ) {
        // Note: Add store authentication here if needed
        return ResponseEntity.ok(orderService.getStoreOrders(storeId, status));
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestHeader("X-Customer-Id") String customerId,
            @RequestBody OrderStatus status
    ) {
        try {
            // Verify the requesting user owns this order
            Order order = orderService.getOrder(orderId, customerId);
            if (!order.getCustomerId().equals(customerId)) {
                return ResponseEntity.status(403)
                    .body(Map.of("error", "Access denied"));
            }
            Order updatedOrder = orderService.updateOrderStatus(orderId, customerId, status);
            return ResponseEntity.ok(updatedOrder);
        } catch (Exception e) {
            return ResponseEntity.status(400)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update order status by restaurant/store
     * This endpoint is used by restaurant owners to update order status
     */
    @PatchMapping("/{orderId}/status/restaurant")
    public ResponseEntity<?> updateOrderStatusByRestaurant(
            @PathVariable String orderId,
            @RequestHeader("X-Store-Id") String storeId,
            @RequestBody OrderStatus status
    ) {
        try {
            Order updatedOrder = orderService.updateOrderStatusByRestaurant(orderId, storeId, status);
            return ResponseEntity.ok(updatedOrder);
        } catch (Exception e) {
            return ResponseEntity.status(400)
                .body(Map.of("error", e.getMessage()));
        }
    }
}