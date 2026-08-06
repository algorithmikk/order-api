package com.umameats.support.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.umameats.chat.model.ChatRole;
import com.umameats.model.Order;
import com.umameats.support.agent.SupportOrderAccess;
import com.umameats.support.agent.SupportTool;
import com.umameats.support.agent.SupportToolContext;
import com.umameats.support.agent.ToolArgs;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single most-used tool: answers "where is my order" with real data instead
 * of a generic apology.
 */
@Component
public class GetOrderStatusTool implements SupportTool {

    private final SupportOrderAccess orderAccess;

    public GetOrderStatusTool(SupportOrderAccess orderAccess) {
        this.orderAccess = orderAccess;
    }

    @Override
    public String name() {
        return "getOrderStatus";
    }

    @Override
    public String description() {
        return "Look up the live status of one of this user's orders, including the driver's name "
                + "and whether it is still in progress. Use this for any question about where an "
                + "order is, when it will arrive, or what is happening with it. Omit orderId to use "
                + "the user's most recent relevant order.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "orderId", Map.of(
                                "type", "string",
                                "description", "Order id. Omit to use the most recent order.")),
                "required", java.util.List.of());
    }

    @Override
    public Set<ChatRole> allowedRoles() {
        return Set.of(ChatRole.CUSTOMER, ChatRole.DRIVER);
    }

    @Override
    public String activityKey() {
        return "support.activity.checkingOrder";
    }

    @Override
    public Map<String, Object> execute(SupportToolContext context, JsonNode arguments) {
        String requestedOrderId = ToolArgs.string(arguments, "orderId", context.thread().getOrderId());

        Optional<Order> order = requestedOrderId != null
                ? orderAccess.findOwned(requestedOrderId, context.principal())
                : orderAccess.mostRecentRelevantOrder(context.principal());

        if (order.isEmpty()) {
            return Map.of("error", requestedOrderId != null
                    ? "No order with that id belongs to this user."
                    : "This user has no orders yet.");
        }

        Map<String, Object> result = new LinkedHashMap<>(orderAccess.summarize(order.get()));
        result.put("inProgress", orderAccess.isActive(order.get()));
        return result;
    }
}
