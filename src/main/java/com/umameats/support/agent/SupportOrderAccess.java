package com.umameats.support.agent;

import com.umameats.chat.model.ChatPrincipal;
import com.umameats.model.Order;
import com.umameats.model.OrderItem;
import com.umameats.model.OrderStatus;
import com.umameats.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Order lookups for the support tools, with ownership enforced in one place.
 *
 * <p>Every read is scoped to the authenticated principal, so no tool can reach
 * another user's order even if the model asks for one by id.
 */
@Service
public class SupportOrderAccess {

    private static final Set<OrderStatus> ACTIVE_STATUSES = EnumSet.of(
            OrderStatus.CREATED,
            OrderStatus.CONFIRMED,
            OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.DRIVER_EN_ROUTE_TO_STORE,
            OrderStatus.DRIVER_SHOPPING,
            OrderStatus.AWAITING_SHOPPING_APPROVAL,
            OrderStatus.SHOPPING_COMPLETE,
            OrderStatus.PICKED_UP,
            OrderStatus.OUT_FOR_DELIVERY);

    /** Cancelling is only fair to the merchant before the food is made. */
    private static final Set<OrderStatus> CANCELLABLE_STATUSES = EnumSet.of(
            OrderStatus.CREATED,
            OrderStatus.CONFIRMED,
            OrderStatus.PENDING_PAYMENT);

    private final OrderRepository orderRepository;

    public SupportOrderAccess(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** The named order, but only if it belongs to the caller. */
    public Optional<Order> findOwned(String orderId, ChatPrincipal principal) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        return orderRepository.findById(orderId, principal.id())
                .filter(order -> owns(order, principal));
    }

    /**
     * The order the user most likely means when they do not name one, newest first.
     */
    public Optional<Order> mostRecentRelevantOrder(ChatPrincipal principal) {
        List<Order> orders = recentOrders(principal, 10);
        return orders.stream()
                .filter(order -> order.getStatus() != null && ACTIVE_STATUSES.contains(order.getStatus()))
                .findFirst()
                .or(() -> orders.stream().findFirst());
    }

    public List<Order> recentOrders(ChatPrincipal principal, int limit) {
        List<Order> orders = principal.isDriver()
                ? driverOrders(principal)
                : new ArrayList<>(orderRepository.findByCustomerId(principal.id()));

        orders.sort(Comparator.comparing(
                Order::getOrderDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return orders.size() > limit ? orders.subList(0, limit) : orders;
    }

    public boolean isCancellable(Order order) {
        return order.getStatus() != null && CANCELLABLE_STATUSES.contains(order.getStatus());
    }

    public boolean isActive(Order order) {
        return order.getStatus() != null && ACTIVE_STATUSES.contains(order.getStatus());
    }

    /**
     * Compact order view for the model. Amounts include a preformatted string so
     * the model never has to divide by 100, which is a reliable source of errors.
     */
    public Map<String, Object> summarize(Order order) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderId", order.getOrderId());
        summary.put("status", order.getStatus() != null ? order.getStatus().name() : "UNKNOWN");
        summary.put("storeName", order.getStoreName());
        summary.put("placedAt", format(order.getOrderDate()));
        summary.put("total", money(order.getTotalAmount()));
        summary.put("itemCount", order.getItems() != null ? order.getItems().size() : 0);
        summary.put("fulfillmentMode", order.getFulfillmentMode());

        if (order.getAssignedDriverName() != null) {
            summary.put("driverName", order.getAssignedDriverName());
        }
        if (order.getDeliveredAt() != null) {
            summary.put("deliveredAt", order.getDeliveredAt());
        }
        if (order.getDeliveryAddress() != null) {
            summary.put("deliveringTo", order.getDeliveryAddress().getStreet());
        }
        summary.put("cancellable", isCancellable(order));
        return summary;
    }

    /** Line-by-line charges, for "why was I charged this" questions. */
    public Map<String, Object> receipt(Order order) {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("orderId", order.getOrderId());
        receipt.put("storeName", order.getStoreName());
        receipt.put("placedAt", format(order.getOrderDate()));

        List<Map<String, Object>> items = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("name", item.getItemName());
                line.put("quantity", item.getQuantity());
                line.put("price", money(item.getPrice() != null ? item.getPrice().longValue() : null));
                if (item.getPickStatus() != null) {
                    line.put("pickStatus", item.getPickStatus());
                }
                if (item.getSubstituteName() != null) {
                    line.put("substitutedWith", item.getSubstituteName());
                }
                items.add(line);
            }
        }
        receipt.put("items", items);
        receipt.put("subtotal", money(order.getSubtotal()));
        receipt.put("deliveryFee", money(order.getDeliveryFee()));
        receipt.put("serviceFee", money(order.getServiceFee()));
        receipt.put("tax", money(order.getTaxAmount()));
        receipt.put("tip", money(order.getTip()));
        receipt.put("total", money(order.getTotalAmount()));
        return receipt;
    }

    public Map<String, Object> money(Long cents) {
        long value = cents != null ? cents : 0L;
        return Map.of(
                "cents", value,
                "formatted", String.format("$%.2f", value / 100.0));
    }

    private boolean owns(Order order, ChatPrincipal principal) {
        if (principal.isDriver()) {
            return principal.id().equals(order.getDriverId())
                    || principal.id().equals(order.getAssignedDriverId())
                    || (principal.email() != null && principal.email().equals(order.getDriverId()));
        }
        return principal.id().equals(order.getCustomerId());
    }

    /**
     * Legacy records store the driver's email in driverId, so both keys are
     * queried and merged, matching how driver-api resolves a driver's orders.
     */
    private List<Order> driverOrders(ChatPrincipal principal) {
        Map<String, Order> byId = new LinkedHashMap<>();
        for (Order order : orderRepository.findByDriverId(principal.id())) {
            byId.put(order.getOrderId(), order);
        }
        if (principal.email() != null && !principal.email().isBlank()) {
            for (Order order : orderRepository.findByDriverId(principal.email())) {
                byId.put(order.getOrderId(), order);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private String format(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toString() : null;
    }
}
