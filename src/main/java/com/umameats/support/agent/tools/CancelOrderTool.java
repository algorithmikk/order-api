package com.umameats.support.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.umameats.chat.model.ChatRole;
import com.umameats.model.Order;
import com.umameats.model.OrderStatus;
import com.umameats.service.OrderService;
import com.umameats.support.agent.SupportOrderAccess;
import com.umameats.support.agent.SupportTool;
import com.umameats.support.agent.SupportToolContext;
import com.umameats.support.agent.ToolArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cancels an order that has not been started yet.
 *
 * <p>The window is intentionally narrow. Once a merchant is cooking or a driver
 * is shopping, cancelling costs someone real money, so those cases go to a human
 * rather than being resolved by the model.
 */
@Slf4j
@Component
public class CancelOrderTool implements SupportTool {

    private final SupportOrderAccess orderAccess;
    private final OrderService orderService;

    public CancelOrderTool(SupportOrderAccess orderAccess, OrderService orderService) {
        this.orderAccess = orderAccess;
        this.orderService = orderService;
    }

    @Override
    public String name() {
        return "cancelOrder";
    }

    @Override
    public String description() {
        return "Cancel an order that the merchant has not started preparing yet. Only call this "
                + "after the user has clearly confirmed they want to cancel. If the order is "
                + "already being prepared or is out for delivery this will refuse, and you should "
                + "escalate to a human instead.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "orderId", Map.of("type", "string", "description", "Order id to cancel."),
                        "reason", Map.of("type", "string", "description", "Why the user wants to cancel.")),
                "required", List.of("orderId"));
    }

    @Override
    public Set<ChatRole> allowedRoles() {
        return Set.of(ChatRole.CUSTOMER);
    }

    @Override
    public String activityKey() {
        return "support.activity.cancellingOrder";
    }

    @Override
    public Map<String, Object> execute(SupportToolContext context, JsonNode arguments) {
        String orderId = ToolArgs.string(arguments, "orderId", context.thread().getOrderId());
        String reason = ToolArgs.string(arguments, "reason", "Cancelled via support assistant");

        Optional<Order> found = orderAccess.findOwned(orderId, context.principal());
        if (found.isEmpty()) {
            return Map.of("error", "No order with that id belongs to this user.");
        }

        Order order = found.get();
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return Map.of("cancelled", true, "note", "This order was already cancelled.");
        }
        if (!orderAccess.isCancellable(order)) {
            return Map.of(
                    "cancelled", false,
                    "reason", "Order is already in status " + order.getStatus()
                            + " and can no longer be self-cancelled.",
                    "suggestion", "Escalate to a human agent to arrange a refund or redelivery.");
        }

        try {
            orderService.updateOrderStatus(order.getOrderId(), order.getCustomerId(), OrderStatus.CANCELLED);
            log.info("Support agent cancelled order {} for customer {} ({})",
                    order.getOrderId(), context.principal().id(), reason);
            return Map.of(
                    "cancelled", true,
                    "orderId", order.getOrderId(),
                    "refundNote", "Any authorised payment is released back to the original method.");
        } catch (Exception e) {
            log.error("Support agent failed to cancel order {}: {}", order.getOrderId(), e.getMessage());
            return Map.of("cancelled", false, "error", "The cancellation could not be completed.");
        }
    }
}
