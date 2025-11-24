package com.umameats.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.umameats.client.EventApiClient;
import com.umameats.client.PaymentApiClient;
import com.umameats.kafka.OrderEventProducer;

import com.umameats.model.DeliveryAddress;
import com.umameats.model.DeliveryEvent;
import com.umameats.model.EventRequest;
import com.umameats.model.Order;
import com.umameats.model.OrderCreatedEvent;
import com.umameats.model.OrderItem;
import com.umameats.model.OrderStatus;
import com.umameats.model.TransactionRequest;
import com.umameats.repository.OrderRepository;

import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;
import java.util.Map;

@Slf4j
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentApiClient paymentApiClient;
    private final EventApiClient eventApiClient;
    private final OrderEventProducer orderEventProducer;
    private final GeocodingService geocodingService;

    public OrderService(OrderRepository orderRepository,
                        PaymentApiClient paymentApiClient,
                        EventApiClient eventApiClient,
                        OrderEventProducer orderEventProducer,
                        GeocodingService geocodingService) {
        this.orderRepository = orderRepository;
        this.paymentApiClient = paymentApiClient;
        this.eventApiClient = eventApiClient;
        this.orderEventProducer = orderEventProducer;
        this.geocodingService = geocodingService;
    }

    public Order createOrder(Order order) {
        // Geocode delivery address if coordinates are missing
        if (order.getDeliveryAddress() != null) {
            DeliveryAddress address = order.getDeliveryAddress();
            if (address.getLatitude() == null || address.getLongitude() == null) {
                log.info("Geocoding delivery address for order");
                GeocodingService.Coordinates coords = geocodingService.geocode(
                    address.getStreet(),
                    address.getCity(),
                    address.getState(),
                    address.getZipCode()
                );

                if (coords != null) {
                    address.setLatitude(coords.getLatitude());
                    address.setLongitude(coords.getLongitude());
                    log.info("Geocoded delivery address to ({}, {})", coords.getLatitude(), coords.getLongitude());
                } else {
                    log.warn("Failed to geocode delivery address - coordinates will be null");
                }
            }
        }

        // Create order first
        order.setOrderId(UUID.randomUUID().toString());
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        Order savedOrder = orderRepository.save(order);

        // Fetch store coordinates for the order created event
        Map<String, Object> storeInfo = fetchStoreInfo(savedOrder.getStoreId());
        Double restaurantLat = null;
        Double restaurantLng = null;
        if (storeInfo != null) {
            restaurantLat = storeInfo.get("latitude") != null ? ((Number) storeInfo.get("latitude")).doubleValue() : null;
            restaurantLng = storeInfo.get("longitude") != null ? ((Number) storeInfo.get("longitude")).doubleValue() : null;
        }

        // Build OrderCreatedEvent with coordinates
        OrderCreatedEvent orderCreatedEvent = OrderCreatedEvent.builder()
                .orderId(savedOrder.getOrderId())
                .restaurantId(savedOrder.getStoreId())
                .restaurantLat(restaurantLat)
                .restaurantLng(restaurantLng)
                .customerId(savedOrder.getCustomerId())
                .customerLat(savedOrder.getDeliveryAddress() != null ? savedOrder.getDeliveryAddress().getLatitude() : null)
                .customerLng(savedOrder.getDeliveryAddress() != null ? savedOrder.getDeliveryAddress().getLongitude() : null)
                .createdAt(System.currentTimeMillis())
                .build();

        // Publish order created event to Kafka
        orderEventProducer.publishOrderCreated(savedOrder.getOrderId(), orderCreatedEvent);
        log.info("Published ORDER_CREATED event for orderId: {} with coordinates (restaurant: {}, {}, customer: {}, {})",
                savedOrder.getOrderId(), restaurantLat, restaurantLng,
                orderCreatedEvent.getCustomerLat(), orderCreatedEvent.getCustomerLng());

        // Fetch store's connected account ID for direct transfer
        String connectedAccountId = fetchStoreConnectedAccountId(savedOrder.getStoreId());

        // Create payment transaction with full payment details
        TransactionRequest transactionRequest = TransactionRequest.builder()
            .orderId(savedOrder.getOrderId())
            .customerId(savedOrder.getCustomerId())
            .storeId(savedOrder.getStoreId())
            .amount(savedOrder.getTotalAmount())
            .paymentMethodId(savedOrder.getPaymentMethodId())
            .currency(savedOrder.getBillingDetails().getCurrency())
            .billingDetails(savedOrder.getBillingDetails())
            .connectedAccountId(connectedAccountId)  // Add connected account for direct transfer
            .build();

        // Call payment API
        paymentApiClient.createTransaction(transactionRequest, order.getCustomerId())
            .subscribe(
                transactionResponse -> {
                    // Payment success will be handled by PaymentEventConsumer
                    log.info("Payment transaction initiated for order: {}", savedOrder.getOrderId());
                },
                error -> {
                    // Payment failure will be handled by PaymentEventConsumer
                    log.error("Payment transaction failed for order: {}", savedOrder.getOrderId(), error);
                }
            );

        return savedOrder;
    }

    /**
     * Fetch store's Stripe Connect account ID from payment-api
     */
    private String fetchStoreConnectedAccountId(String storeId) {
        try {
            // Call payment-api to get store's payout method
            String url = "https://api.umameats.com/api/v1/payments/payout-methods/store/" + storeId;

            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.ResponseEntity<java.util.List> response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                null,
                java.util.List.class
            );

            if (response.getBody() != null && !response.getBody().isEmpty()) {
                // Get the first (default) payout method
                java.util.Map<String, Object> payoutMethod = (java.util.Map<String, Object>) response.getBody().get(0);
                String connectedAccountId = (String) payoutMethod.get("connectedAccountId");

                if (connectedAccountId != null) {
                    log.info("Found connected account ID for store {}: {}", storeId, connectedAccountId);
                    return connectedAccountId;
                }
            }

            log.warn("No connected account found for store: {}", storeId);
            return null;

        } catch (Exception e) {
            log.error("Error fetching connected account for store {}: {}", storeId, e.getMessage());
            return null;
        }
    }

    /**
     * Fetch store information from store-api-rest
     */
    private Map<String, Object> fetchStoreInfo(String storeId) {
        try {
            String url = "https://api.umameats.com/api/v1/stores/" + storeId;

            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                null,
                Map.class
            );

            if (response.getBody() != null) {
                log.info("Fetched store info for store {}", storeId);
                return response.getBody();
            }

            log.warn("No store info found for store: {}", storeId);
            return null;

        } catch (Exception e) {
            log.error("Error fetching store info for store {}: {}", storeId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Creates and sends a delivery event for the given order
     *
     * @param order The order to create a delivery event for
     */
    private void createAndSendDeliveryEvent(Order order) {
        try {
            // Fetch store info and populate order with pickup details
            Map<String, Object> storeInfo = fetchStoreInfo(order.getStoreId());
            if (storeInfo != null) {
                order.setStoreName((String) storeInfo.get("name"));
                order.setStorePhone((String) storeInfo.get("phoneNumber"));
                order.setPickupAddress(formatStoreAddress(storeInfo));
            }

            // Initialize delivery assignment status
            order.setDeliveryStatus("UNASSIGNED");
            order.setAssignedDriverId(null);
            order.setAssignedDriverName(null);
            order.setAssignedDriverPhone(null);
            order.setAssignedAt(null);
            order.setAcceptedAt(null);

            // Save the updated order with pickup details and delivery status
            orderRepository.save(order);
            log.info("Updated order {} with store pickup details and delivery status UNASSIGNED", order.getOrderId());

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
                        // Order status is already READY_FOR_PICKUP, no need to update again
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

        // Fetch store info and create pickup location
        DeliveryEvent.Location pickupLocation = null;
        Map<String, Object> storeInfo = fetchStoreInfo(order.getStoreId());
        if (storeInfo != null) {
            // Extract coordinates from store info (may be null if not geocoded yet)
            Double storeLat = storeInfo.get("latitude") != null ? ((Number) storeInfo.get("latitude")).doubleValue() : 0.00;
            Double storeLng = storeInfo.get("longitude") != null ? ((Number) storeInfo.get("longitude")).doubleValue() : 0.00;

            pickupLocation = DeliveryEvent.Location.builder()
                    .address((String) storeInfo.get("address"))
                    .city((String) storeInfo.get("city"))
                    .state((String) storeInfo.get("state"))
                    .postalCode((String) storeInfo.get("zipCode"))
                    .latitude(storeLat)
                    .longitude(storeLng)
                    .formattedAddress(formatStoreAddress(storeInfo))
                    .build();
        }

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
                .pickupLocation(pickupLocation)
                .deliveryLocation(deliveryLocation)
                .specialInstructions(order.getSpecialInstructions())
                .orderInfo(orderInfo)
                .build();
    }

    /**
     * Format store address into a single string
     */
    private String formatStoreAddress(Map<String, Object> storeInfo) {
        if (storeInfo == null) {
            return null;
        }

        StringBuilder address = new StringBuilder();

        String storeName = (String) storeInfo.get("name");
        if (storeName != null && !storeName.isEmpty()) {
            address.append(storeName).append(", ");
        }

        String street = (String) storeInfo.get("address");
        if (street != null && !street.isEmpty()) {
            address.append(street);
        }

        String city = (String) storeInfo.get("city");
        String state = (String) storeInfo.get("state");
        String zipCode = (String) storeInfo.get("zipCode");

        if (city != null || state != null || zipCode != null) {
            address.append(", ");
            if (city != null) address.append(city);
            if (state != null) address.append(", ").append(state);
            if (zipCode != null) address.append(" ").append(zipCode);
        }

        return address.toString();
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
                .latitude(address.getLatitude() != null ? address.getLatitude() : 0.00)
                .longitude(address.getLongitude() != null ? address.getLongitude() : 0.00)
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
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        // Publish order status change event
        orderEventProducer.publishOrderStatusChange(orderId, newStatus.toString(), updatedOrder);

        // Publish customer notification
        Map<String, Object> customerNotification = Map.of(
            "eventType", "ORDER_STATUS_UPDATED",
            "orderId", orderId,
            "customerId", customerId,
            "oldStatus", oldStatus.toString(),
            "newStatus", newStatus.toString(),
            "timestamp", System.currentTimeMillis()
        );
        orderEventProducer.publishCustomerNotification(customerId, orderId, customerNotification);

        return updatedOrder;
    }

    /**
     * Updates order status by restaurant/store
     * Triggers delivery event when status becomes READY_FOR_PICKUP
     *
     * @param orderId The order ID
     * @param storeId The store ID (for authorization)
     * @param newStatus The new status
     * @return Updated order
     */
    public Order updateOrderStatusByRestaurant(String orderId, String storeId, OrderStatus newStatus) {
        // Find the order
        Order order = orderRepository.findById(orderId, null)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Verify the store owns this order
        if (!order.getStoreId().equals(storeId)) {
            throw new RuntimeException("Access denied - order does not belong to this store");
        }

        // Validate status transition
        validateRestaurantStatusTransition(order.getStatus(), newStatus);

        // Update status
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        log.info("Restaurant updated order {} status from {} to {}", orderId, oldStatus, newStatus);

        // Publish order status change event
        orderEventProducer.publishOrderStatusChange(orderId, newStatus.toString(), updatedOrder);

        // Publish customer notification
        Map<String, Object> customerNotification = Map.of(
            "eventType", "ORDER_STATUS_UPDATED",
            "orderId", orderId,
            "customerId", updatedOrder.getCustomerId(),
            "oldStatus", oldStatus.toString(),
            "newStatus", newStatus.toString(),
            "timestamp", System.currentTimeMillis()
        );
        orderEventProducer.publishCustomerNotification(updatedOrder.getCustomerId(), orderId, customerNotification);

        // If status changed to READY_FOR_PICKUP, create and send delivery event
        if (newStatus == OrderStatus.READY_FOR_PICKUP && oldStatus != OrderStatus.READY_FOR_PICKUP) {
            log.info("Order {} is ready for pickup, creating delivery event", orderId);
            createAndSendDeliveryEvent(updatedOrder);
        }

        return updatedOrder;
    }

    /**
     * Validates that the restaurant can transition from old status to new status
     */
    private void validateRestaurantStatusTransition(OrderStatus oldStatus, OrderStatus newStatus) {
        // Restaurant can only update certain statuses
        if (oldStatus == OrderStatus.PENDING_PAYMENT || oldStatus == OrderStatus.PAYMENT_FAILED) {
            throw new RuntimeException("Cannot update order status - payment not completed");
        }

        // Valid transitions for restaurant:
        // CREATED -> PREPARING
        // PREPARING -> READY_FOR_PICKUP
        // Any -> CANCELLED

        if (newStatus == OrderStatus.CANCELLED) {
            return; // Can always cancel
        }

        if (oldStatus == OrderStatus.CREATED && newStatus == OrderStatus.PREPARING) {
            return; // Valid
        }

        if (oldStatus == OrderStatus.PREPARING && newStatus == OrderStatus.READY_FOR_PICKUP) {
            return; // Valid
        }

        // Allow going directly from CREATED to READY_FOR_PICKUP (skip PREPARING)
        if (oldStatus == OrderStatus.CREATED && newStatus == OrderStatus.READY_FOR_PICKUP) {
            return; // Valid
        }

        throw new RuntimeException("Invalid status transition from " + oldStatus + " to " + newStatus);
    }
}