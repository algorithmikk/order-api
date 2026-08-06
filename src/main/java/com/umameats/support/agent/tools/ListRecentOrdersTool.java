package com.umameats.support.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.umameats.chat.model.ChatRole;
import com.umameats.support.agent.SupportOrderAccess;
import com.umameats.support.agent.SupportTool;
import com.umameats.support.agent.SupportToolContext;
import com.umameats.support.agent.ToolArgs;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lets the agent disambiguate when the user says "my last order" or "the one on Tuesday". */
@Component
public class ListRecentOrdersTool implements SupportTool {

    private static final int MAX_LIMIT = 10;

    private final SupportOrderAccess orderAccess;

    public ListRecentOrdersTool(SupportOrderAccess orderAccess) {
        this.orderAccess = orderAccess;
    }

    @Override
    public String name() {
        return "listRecentOrders";
    }

    @Override
    public String description() {
        return "List this user's recent orders, newest first, with status and total. Use this when "
                + "the user refers to an order without identifying which one, so you can ask a "
                + "precise follow-up question or pick the obvious match.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of(
                                "type", "integer",
                                "description", "How many orders to return, 1 to " + MAX_LIMIT + ".")),
                "required", List.of());
    }

    @Override
    public Set<ChatRole> allowedRoles() {
        return Set.of(ChatRole.CUSTOMER, ChatRole.DRIVER);
    }

    @Override
    public String activityKey() {
        return "support.activity.lookingUpOrders";
    }

    @Override
    public Map<String, Object> execute(SupportToolContext context, JsonNode arguments) {
        int limit = Math.clamp(ToolArgs.integer(arguments, "limit", 5), 1, MAX_LIMIT);

        List<Map<String, Object>> orders = orderAccess.recentOrders(context.principal(), limit)
                .stream()
                .map(orderAccess::summarize)
                .toList();

        return Map.of("orders", orders, "count", orders.size());
    }
}
