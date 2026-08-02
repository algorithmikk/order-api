package com.umameats.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.umameats.model.Order;
import com.umameats.model.OrderStatus;
import com.umameats.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpsOrderService {

    private static final Set<OrderStatus> DEFAULT_ACTIVE = EnumSet.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAYMENT_FAILED,
            OrderStatus.CREATED,
            OrderStatus.CONFIRMED,
            OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.DRIVER_EN_ROUTE_TO_STORE,
            OrderStatus.PICKED_UP,
            OrderStatus.OUT_FOR_DELIVERY);

    private static final Set<OrderStatus> TERMINAL = EnumSet.of(
            OrderStatus.DELIVERED,
            OrderStatus.CANCELLED);

    private final OrderRepository orderRepository;

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
