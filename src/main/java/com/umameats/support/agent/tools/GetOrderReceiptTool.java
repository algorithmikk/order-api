package com.umameats.support.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.umameats.chat.model.ChatRole;
import com.umameats.model.Order;
import com.umameats.support.agent.SupportOrderAccess;
import com.umameats.support.agent.SupportTool;
import com.umameats.support.agent.SupportToolContext;
import com.umameats.support.agent.ToolArgs;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Itemised charges, for billing questions and missing-item claims. */
@Component
public class GetOrderReceiptTool implements SupportTool {

    private final SupportOrderAccess orderAccess;

    public GetOrderReceiptTool(SupportOrderAccess orderAccess) {
        this.orderAccess = orderAccess;
    }

    @Override
    public String name() {
        return "getOrderReceipt";
    }

    @Override
    public String description() {
        return "Get the itemised receipt for an order: every line item, any substitutions made "
                + "while shopping, plus subtotal, delivery fee, service fee, tax and tip. Use this "
                + "for questions about charges, missing items or unexpected totals.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "orderId", Map.of(
                                "type", "string",
                                "description", "Order id. Omit to use the most recent order.")),
                "required", List.of());
    }

    @Override
    public Set<ChatRole> allowedRoles() {
        return Set.of(ChatRole.CUSTOMER);
    }

    @Override
    public String activityKey() {
        return "support.activity.readingReceipt";
    }

    @Override
    public Map<String, Object> execute(SupportToolContext context, JsonNode arguments) {
        String requestedOrderId = ToolArgs.string(arguments, "orderId", context.thread().getOrderId());

        Optional<Order> order = requestedOrderId != null
                ? orderAccess.findOwned(requestedOrderId, context.principal())
                : orderAccess.mostRecentRelevantOrder(context.principal());

        return order.map(orderAccess::receipt)
                .orElseGet(() -> Map.of("error", "No matching order for this user."));
    }
}
