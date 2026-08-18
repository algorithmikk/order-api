package com.umameats.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.umameats.kafka.OrderEventProducer;
import com.umameats.model.BillingDetails;
import com.umameats.model.DeliveryAddress;
import com.umameats.model.FulfillmentMode;
import com.umameats.model.OpsStatusUpdateRequest;
import com.umameats.model.Order;
import com.umameats.model.OrderCreatedEvent;
import com.umameats.model.OrderItem;
import com.umameats.model.OrderStatus;
import com.umameats.model.SeedDemoOrderRequest;
import com.umameats.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpsOrderService {

    private static final String DEFAULT_CUSTOMER_ID = "7cc3f702-ba3d-48a8-8238-263fd3f31eab";
    private static final String API_BASE = "https://api.umameats.com/api/v1";

    private static final Set<OrderStatus> DEFAULT_ACTIVE = EnumSet.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAYMENT_FAILED,
            OrderStatus.CREATED,
            OrderStatus.CONFIRMED,
            OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.DRIVER_EN_ROUTE_TO_STORE,
            OrderStatus.PICKING_UP,
            OrderStatus.PICKED_UP,
            OrderStatus.OUT_FOR_DELIVERY);

    private static final Set<OrderStatus> TERMINAL = EnumSet.of(
            OrderStatus.DELIVERED,
            OrderStatus.CANCELLED);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderEventProducer orderEventProducer;
    private final PricingService pricingService;
    private final TaxService taxService;

    /**
     * List orders for the ops control center board.
     * When statuses is null/empty, query all active statuses plus recent terminals (24h).
     */
    public List<Order> listOrders(List<OrderStatus> statuses) {
        Set<OrderStatus> toQuery = new HashSet<>();
        if (statuses == null || statuses.isEmpty()) {
            toQuery.addAll(DEFAULT_ACTIVE);
            toQuery.addAll(TERMINAL);
        } else {
            toQuery.addAll(statuses);
        }

        List<CompletableFuture<List<Order>>> futures = toQuery.stream()
                .map(status -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return orderRepository.findByStatus(status);
                    } catch (Exception e) {
                        log.error("Ops status-index query failed for {}", status, e);
                        return List.<Order>of();
                    }
                }))
                .collect(Collectors.toList());

        List<Order> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        for (CompletableFuture<List<Order>> future : futures) {
            for (Order order : future.join()) {
                if (order == null || order.getOrderId() == null) {
                    continue;
                }
                if (!seen.add(order.getOrderId())) {
                    continue;
                }
                if (order.getStatus() != null && TERMINAL.contains(order.getStatus())) {
                    LocalDateTime placed = order.getOrderDate();
                    if (placed == null || placed.isBefore(cutoff)) {
                        continue;
                    }
                }
                merged.add(order);
            }
        }

        merged.sort(Comparator.comparing(
                Order::getOrderDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        log.info("Ops board loaded {} orders across {} statuses", merged.size(), toQuery.size());
        return merged;
    }

    /**
     * Create a demo order already in CREATED (or READY_FOR_PICKUP) without Stripe.
     */
    public Map<String, Object> seedDemoOrder(SeedDemoOrderRequest request) {
        if (request == null || request.getStoreId() == null || request.getStoreId().isBlank()) {
            throw new IllegalArgumentException("storeId is required");
        }

        String customerId = request.getCustomerId() != null && !request.getCustomerId().isBlank()
                ? request.getCustomerId()
                : DEFAULT_CUSTOMER_ID;
        String mode = request.getMode() != null ? request.getMode().trim().toUpperCase() : "CREATED";
        long tipCents = request.getTipCents() != null ? request.getTipCents() : 300L;

        Map<String, Object> storeInfo = fetchStoreInfo(request.getStoreId());
        if (storeInfo == null) {
            throw new IllegalArgumentException("Store not found: " + request.getStoreId());
        }

        List<OrderItem> items = fetchMenuItemsAsOrderItems(request.getStoreId());
        if (items.isEmpty()) {
            OrderItem fallback = new OrderItem();
            fallback.setItemId("demo-item");
            fallback.setItemName("Demo Kitchen Plate");
            fallback.setQuantity(1);
            fallback.setPrice(1899.0);
            items = List.of(fallback);
        }

        DeliveryAddress delivery = new DeliveryAddress();
        delivery.setFullName("App Review Customer");
        delivery.setPhone("4165550137");
        delivery.setStreet("100 Queens Park");
        delivery.setCity("Toronto");
        delivery.setState("ON");
        delivery.setZipCode("M5S 2C6");
        delivery.setCountry("CA");
        delivery.setLatitude(43.6677);
        delivery.setLongitude(-79.3948);

        BillingDetails billing = new BillingDetails();
        billing.setName("App Review Customer");
        billing.setEmail("appreview@umameats.com");
        billing.setPhone("4165550137");
        billing.setCurrency("CAD");

        Order order = new Order();
        order.setOrderId(UUID.randomUUID().toString());
        order.setStoreId(request.getStoreId());
        order.setCustomerId(customerId);
        order.setItems(items);
        order.setDeliveryAddress(delivery);
        order.setBillingDetails(billing);
        order.setPaymentMethod("CARD");
        order.setPaymentMethodId("ops_seed_demo");
        order.setOrderDate(LocalDateTime.now());
        boolean proxyMode = "PROXY".equals(mode) || "DRIVER_PROXY".equals(mode);
        order.setSpecialInstructions(proxyMode
                ? "E2E demo seed — DRIVER_PROXY launch path (no kitchen)"
                : "E2E demo seed — safe to advance through kitchen → driver");
        order.setTip(tipCents);

        String storeName = (String) storeInfo.get("name");
        String storePhone = storeInfo.get("phoneNumber") != null
                ? String.valueOf(storeInfo.get("phoneNumber"))
                : (storeInfo.get("phone") != null ? String.valueOf(storeInfo.get("phone")) : "+14165550199");
        Double restaurantLat = storeInfo.get("latitude") != null
                ? ((Number) storeInfo.get("latitude")).doubleValue() : 43.6426;
        Double restaurantLng = storeInfo.get("longitude") != null
                ? ((Number) storeInfo.get("longitude")).doubleValue() : -79.3871;
        String pickupAddress = formatStoreAddress(storeInfo);

        order.setStoreName(storeName != null ? storeName : "UmaMeats Demo Kitchen");
        order.setStorePhone(storePhone);
        order.setPickupAddress(pickupAddress);
        order.setRestaurantLat(restaurantLat);
        order.setRestaurantLng(restaurantLng);

        long subtotal = pricingService.calculateSubtotal(items);
        long deliveryFee = pricingService.calculateDeliveryFeeFromSubtotal(subtotal);
        long customerDeliveryFee = deliveryFee;
        long serviceFee = pricingService.calculateServiceFee(subtotal);
        long tip = pricingService.validateTip(tipCents);
        long platformFee = pricingService.calculatePlatformFee(subtotal);
        TaxService.TaxResult taxResult = taxService.calculateTax(subtotal, customerDeliveryFee, serviceFee, "CA", "ON");
        long totalAmount = subtotal + customerDeliveryFee + serviceFee + tip + taxResult.getTotalTax();

        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setCustomerDeliveryFee(customerDeliveryFee);
        order.setServiceFee(serviceFee);
        order.setTip(tip);
        order.setPlatformFee(platformFee);
        order.setTaxAmount(taxResult.getTotalTax());
        order.setTaxRate(taxResult.getTaxRate());
        order.setTaxBreakdown(taxResult.toBreakdownJson());
        order.setTotalAmount(totalAmount);

        boolean readyMode = "READY".equals(mode) || "READY_FOR_PICKUP".equals(mode);
        if (proxyMode) {
            order.setFulfillmentMode(FulfillmentMode.DRIVER_PROXY);
            order.setMerchantType("MERCHANT_TYPE_RESTAURANT");
        }
        order.setStatus(OrderStatus.CREATED);
        order.setDeliveryStatus("UNASSIGNED");
        OrderService.ensureDeliveryPin(order);

        Order saved = orderRepository.save(order);
        log.info("Ops seed-demo created order {} for store {} (mode={})", saved.getOrderId(), saved.getStoreId(), mode);

        if (readyMode && !proxyMode) {
            // Skip Kafka-heavy opsUpdateStatus path for demos — write READY/UNASSIGNED directly.
            saved.setStatus(OrderStatus.READY_FOR_PICKUP);
            saved.setDeliveryStatus("UNASSIGNED");
            saved.setDriverId(null);
            saved.setAssignedDriverId(null);
            saved.setAssignedDriverName(null);
            saved.setAssignedDriverPhone(null);
            saved.setAssignedAt(null);
            saved.setAcceptedAt(null);
            saved = orderRepository.save(saved);
            log.info("Ops seed-demo marked order {} READY_FOR_PICKUP / UNASSIGNED", saved.getOrderId());
        }

        if (proxyMode) {
            // Open marketplace the same way payment success would for DRIVER_PROXY.
            orderService.dispatchImmediateIfNeeded(saved);
        }

        // Kafka metadata waits can block the request for 60s+ when topics are missing.
        // Publish off-thread so the HTTP response returns after Dynamo save.
        final Order kafkaOrder = saved;
        final String kafkaCustomerId = customerId;
        final long kafkaTotal = totalAmount;
        final boolean skipStoreNotify = proxyMode;
        Thread publisher = new Thread(() -> {
            try {
                OrderCreatedEvent createdEvent = OrderCreatedEvent.builder()
                        .orderId(kafkaOrder.getOrderId())
                        .restaurantId(kafkaOrder.getStoreId())
                        .restaurantLat(restaurantLat)
                        .restaurantLng(restaurantLng)
                        .customerId(kafkaCustomerId)
                        .customerLat(delivery.getLatitude())
                        .customerLng(delivery.getLongitude())
                        .createdAt(System.currentTimeMillis())
                        .build();
                orderEventProducer.publishOrderCreated(kafkaOrder.getOrderId(), createdEvent);
                orderEventProducer.publishOrderStatusChange(
                        kafkaOrder.getOrderId(),
                        kafkaOrder.getStatus() != null ? kafkaOrder.getStatus().toString() : "CREATED",
                        kafkaOrder);
                if (!skipStoreNotify) {
                    orderEventProducer.publishStoreNotification(
                            kafkaOrder.getStoreId(),
                            kafkaOrder.getOrderId(),
                            Map.of(
                                    "eventType", "NEW_ORDER",
                                    "orderId", kafkaOrder.getOrderId(),
                                    "storeId", kafkaOrder.getStoreId(),
                                    "customerId", kafkaCustomerId,
                                    "totalAmount", kafkaTotal,
                                    "timestamp", System.currentTimeMillis()
                            ));
                }
            } catch (Exception e) {
                log.warn("Ops seed-demo Kafka publish failed for {} (order still saved): {}",
                        kafkaOrder.getOrderId(), e.getMessage());
            }
        }, "ops-seed-kafka-" + saved.getOrderId());
        publisher.setDaemon(true);
        publisher.start();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", saved.getOrderId());
        body.put("storeId", saved.getStoreId());
        body.put("status", saved.getStatus() != null ? saved.getStatus().toString() : null);
        body.put("deliveryStatus", saved.getDeliveryStatus());
        body.put("fulfillmentMode", saved.getFulfillmentMode());
        body.put("customerId", customerId);
        body.put("totalAmount", saved.getTotalAmount());
        return body;
    }

    public Order forceUpdateStatus(String orderId, OpsStatusUpdateRequest request) {
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        OrderStatus status = parseStatus(request.getStatus());
        if (status == null) {
            throw new IllegalArgumentException("Invalid status: " + request.getStatus());
        }
        boolean clearDriver = Boolean.TRUE.equals(request.getClearDriver())
                || status == OrderStatus.READY_FOR_PICKUP;

        Order updated = orderService.opsUpdateStatus(
                orderId,
                status,
                clearDriver,
                request.getRestaurantLat(),
                request.getRestaurantLng()
        );

        if (request.getDeliveryStatus() != null && !request.getDeliveryStatus().isBlank()) {
            updated.setDeliveryStatus(request.getDeliveryStatus());
            updated = orderRepository.save(updated);
        }

        log.info("Ops force status {} on order {} (note={})", status, orderId, request.getNote());
        return updated;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchStoreInfo(String storeId) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_BASE + "/stores/" + storeId,
                    HttpMethod.GET,
                    null,
                    Map.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch store {}: {}", storeId, e.getMessage());
            return null;
        }
    }

    private List<OrderItem> fetchMenuItemsAsOrderItems(String storeId) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    API_BASE + "/menu-items/store/" + storeId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> menu = response.getBody();
            if (menu == null || menu.isEmpty()) {
                return List.of();
            }
            List<OrderItem> items = new ArrayList<>();
            int limit = Math.min(2, menu.size());
            for (int i = 0; i < limit; i++) {
                Map<String, Object> m = menu.get(i);
                OrderItem item = new OrderItem();
                item.setItemId(m.get("itemId") != null ? String.valueOf(m.get("itemId"))
                        : (m.get("id") != null ? String.valueOf(m.get("id")) : UUID.randomUUID().toString()));
                item.setItemName(m.get("name") != null ? String.valueOf(m.get("name")) : "Menu item");
                item.setQuantity(1);
                long cents = 0L;
                if (m.get("priceCents") instanceof Number) {
                    cents = ((Number) m.get("priceCents")).longValue();
                } else if (m.get("price") instanceof Number) {
                    double p = ((Number) m.get("price")).doubleValue();
                    cents = p > 100 ? Math.round(p) : Math.round(p * 100);
                }
                item.setPrice((double) cents);
                items.add(item);
            }
            return items;
        } catch (Exception e) {
            log.warn("Could not load menu for store {}: {}", storeId, e.getMessage());
            return List.of();
        }
    }

    private String formatStoreAddress(Map<String, Object> storeInfo) {
        String address = storeInfo.get("address") != null ? String.valueOf(storeInfo.get("address")) : "";
        String city = storeInfo.get("city") != null ? String.valueOf(storeInfo.get("city")) : "";
        String state = storeInfo.get("state") != null ? String.valueOf(storeInfo.get("state")) : "";
        String zip = storeInfo.get("zipCode") != null ? String.valueOf(storeInfo.get("zipCode")) : "";
        StringBuilder sb = new StringBuilder();
        if (!address.isBlank()) sb.append(address);
        if (!city.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        if (!state.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(state);
        }
        if (!zip.isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(zip);
        }
        return sb.length() > 0 ? sb.toString() : "250 Front St W, Toronto, ON M5V 3G5";
    }

    public static OrderStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static List<OrderStatus> parseStatuses(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .map(OpsOrderService::parseStatus)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }
}
