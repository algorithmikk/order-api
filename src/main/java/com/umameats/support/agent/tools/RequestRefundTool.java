package com.umameats.support.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.umameats.chat.model.ChatRole;
import com.umameats.model.Order;
import com.umameats.support.agent.SupportOrderAccess;
import com.umameats.support.agent.SupportTool;
import com.umameats.support.agent.SupportToolContext;
import com.umameats.support.agent.ToolArgs;
import com.umameats.support.config.SupportProperties;
import com.umameats.support.model.SupportThread;
import com.umameats.support.model.SupportThreadState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Approves a goodwill refund within hard limits, then queues it for settlement.
 *
 * <p>This records an approval rather than calling Stripe directly. payment-api
 * exposes no refund endpoint today, and wiring an LLM straight into an untested
 * money-movement path is not a risk worth taking. The customer gets a firm answer
 * immediately, and ops settles against an auditable record.
 *
 * <p>Three limits apply: an absolute cap, a share of the order total, and a
 * per-thread running total so repeated asks across turns cannot stack up.
 */
@Slf4j
@Component
public class RequestRefundTool implements SupportTool {

    private final SupportOrderAccess orderAccess;
    private final SupportProperties properties;

    public RequestRefundTool(SupportOrderAccess orderAccess, SupportProperties properties) {
        this.orderAccess = orderAccess;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "requestRefund";
    }

    @Override
    public String description() {
        return "Approve a refund for a genuine problem such as missing items, a cold or incorrect "
                + "order, or a delivery that never arrived. State the amount in cents. Small "
                + "refunds are approved immediately; anything larger is handed to a human "
                + "automatically, so always call this rather than promising a refund yourself.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "orderId", Map.of("type", "string", "description", "Order to refund."),
                        "amountCents", Map.of(
                                "type", "integer",
                                "description", "Refund amount in cents, never more than the order total."),
                        "reason", Map.of(
                                "type", "string",
                                "description", "Specific reason, e.g. 'two items missing from the bag'.")),
                "required", List.of("orderId", "amountCents", "reason"));
    }

    @Override
    public Set<ChatRole> allowedRoles() {
        return Set.of(ChatRole.CUSTOMER);
    }

    @Override
    public String activityKey() {
        return "support.activity.reviewingRefund";
    }

    @Override
    public Map<String, Object> execute(SupportToolContext context, JsonNode arguments) {
        String orderId = ToolArgs.string(arguments, "orderId", context.thread().getOrderId());
        long requestedCents = ToolArgs.number(arguments, "amountCents", 0);
        String reason = ToolArgs.string(arguments, "reason", "Not specified");

        if (requestedCents <= 0) {
            return Map.of("approved", false, "error", "Refund amount must be greater than zero.");
        }

        Optional<Order> found = orderAccess.findOwned(orderId, context.principal());
        if (found.isEmpty()) {
            return Map.of("approved", false, "error", "No order with that id belongs to this user.");
        }

        Order order = found.get();
        SupportThread thread = context.thread();

        long orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : 0L;
        long alreadyApproved = thread.getRefundedCents() != null ? thread.getRefundedCents() : 0L;
        long cumulative = alreadyApproved + requestedCents;

        long absoluteCap = properties.getRefund().getMaxAutoRefundCents();
        long proportionalCap = Math.round(orderTotal * properties.getRefund().getMaxAutoRefundFraction());

        if (requestedCents > orderTotal) {
            return Map.of(
                    "approved", false,
                    "reason", "Refund exceeds the order total of " + orderAccess.money(orderTotal).get("formatted"));
        }
        if (cumulative > absoluteCap || cumulative > proportionalCap) {
            thread.setState(SupportThreadState.WAITING_HUMAN.name());
            thread.setEscalatedAt(System.currentTimeMillis());
            thread.setEscalationReason("Refund above automatic limit: " + reason);

            log.info("Refund of {} cents on order {} exceeds limits; escalating thread {}",
                    requestedCents, order.getOrderId(), thread.getThreadId());

            return Map.of(
                    "approved", false,
                    "escalated", true,
                    "reason", "This refund is above the amount that can be approved automatically.",
                    "instruction", "Tell the user a support specialist will review it shortly. "
                            + "Do not promise an amount or a timeframe.");
        }

        thread.setRefundedCents(cumulative);
        if (thread.getOrderId() == null) {
            thread.setOrderId(order.getOrderId());
        }

        log.info("Support agent approved refund of {} cents on order {} for customer {}: {}",
                requestedCents, order.getOrderId(), context.principal().id(), reason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approved", true);
        result.put("orderId", order.getOrderId());
        result.put("amount", orderAccess.money(requestedCents));
        result.put("reason", reason);
        result.put("settlement", "Queued for processing to the original payment method.");
        result.put("expectedBusinessDays", "3-5");
        return result;
    }
}
