package com.umameats.support.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.umameats.chat.model.ChatRole;
import com.umameats.model.Order;
import com.umameats.support.agent.SupportOrderAccess;
import com.umameats.support.agent.SupportTool;
import com.umameats.support.agent.SupportToolContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** What the driver is currently supposed to be doing, and where. */
@Component
public class GetAssignedDeliveryTool implements SupportTool {

    private final SupportOrderAccess orderAccess;

    public GetAssignedDeliveryTool(SupportOrderAccess orderAccess) {
        this.orderAccess = orderAccess;
    }

    @Override
    public String name() {
        return "getAssignedDelivery";
    }

    @Override
    public String description() {
        return "Get the driver's current delivery assignment: the store, the customer's address, "
                + "the order status and what step comes next. Use this for any question about the "
                + "delivery they are on right now.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Set<ChatRole> allowedRoles() {
        return Set.of(ChatRole.DRIVER);
    }

    @Override
    public String activityKey() {
        return "support.activity.checkingDelivery";
    }

    @Override
    public Map<String, Object> execute(SupportToolContext context, JsonNode arguments) {
        Optional<Order> active = orderAccess.recentOrders(context.principal(), 10).stream()
                .filter(orderAccess::isActive)
                .findFirst();

        if (active.isEmpty()) {
            return Map.of("assigned", false, "note", "This driver has no active delivery right now.");
        }

        Order order = active.get();
        Map<String, Object> result = new LinkedHashMap<>(orderAccess.summarize(order));
        result.put("assigned", true);
        if (order.getDeliveryAddress() != null) {
            result.put("dropOffAddress", order.getDeliveryAddress().getStreet());
            result.put("dropOffCity", order.getDeliveryAddress().getCity());
        }
        result.put("deliveryFee", orderAccess.money(order.getDeliveryFee()));
        result.put("tip", orderAccess.money(order.getTip()));
        return result;
    }
}
