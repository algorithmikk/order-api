package com.umameats.support.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.umameats.chat.model.ChatRole;
import com.umameats.model.Order;
import com.umameats.model.OrderStatus;
import com.umameats.support.agent.SupportOrderAccess;
import com.umameats.support.agent.SupportTool;
import com.umameats.support.agent.SupportToolContext;
import com.umameats.support.agent.ToolArgs;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Earnings from completed deliveries over a recent window.
 *
 * <p>Computed from the orders themselves rather than by calling driver-api, which
 * keeps this a single Dynamo query with no cross-service hop on a support path.
 * It answers "what did I make yesterday" precisely; the Earnings screen remains
 * the authority on payouts and adjustments.
 */
@Component
public class GetEarningsSummaryTool implements SupportTool {

    private static final int MAX_DAYS = 30;

    private final SupportOrderAccess orderAccess;

    public GetEarningsSummaryTool(SupportOrderAccess orderAccess) {
        this.orderAccess = orderAccess;
    }

    @Override
    public String name() {
        return "getEarningsSummary";
    }

    @Override
    public String description() {
        return "Summarise what this driver earned from completed deliveries over the last N days, "
                + "broken down into delivery fees and tips. Use this for questions about pay, tips "
                + "or how many deliveries they have completed recently.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "days", Map.of(
                                "type", "integer",
                                "description", "Look-back window in days, 1 to " + MAX_DAYS + ".")),
                "required", List.of());
    }

    @Override
    public Set<ChatRole> allowedRoles() {
        return Set.of(ChatRole.DRIVER);
    }

    @Override
    public String activityKey() {
        return "support.activity.checkingEarnings";
    }

    @Override
    public Map<String, Object> execute(SupportToolContext context, JsonNode arguments) {
        int days = Math.clamp(ToolArgs.integer(arguments, "days", 7), 1, MAX_DAYS);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<Order> delivered = orderAccess.recentOrders(context.principal(), 200).stream()
                .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                .filter(order -> order.getOrderDate() != null && order.getOrderDate().isAfter(since))
                .toList();

        long fees = delivered.stream().mapToLong(order -> value(order.getDeliveryFee())).sum();
        long tips = delivered.stream().mapToLong(order -> value(order.getTip())).sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("completedDeliveries", delivered.size());
        result.put("deliveryFees", orderAccess.money(fees));
        result.put("tips", orderAccess.money(tips));
        result.put("total", orderAccess.money(fees + tips));
        result.put("note", "Delivery earnings only. The Earnings screen is authoritative for "
                + "payouts, bonuses and adjustments.");
        return result;
    }

    private long value(Long amount) {
        return amount != null ? amount : 0L;
    }
}
