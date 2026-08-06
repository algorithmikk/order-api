package com.umameats.support.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.umameats.chat.model.ChatRole;
import com.umameats.model.Order;
import com.umameats.support.agent.SupportOrderAccess;
import com.umameats.support.agent.SupportTool;
import com.umameats.support.agent.SupportToolContext;
import com.umameats.support.agent.ToolArgs;
import com.umameats.support.model.SupportThread;
import com.umameats.support.model.SupportThreadState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Files a blocked-delivery report from the driver.
 *
 * <p>These need a person: unassigning a delivery, compensating a wait, or
 * closing out an undeliverable order all affect the customer and the driver's
 * pay, so the tool records the details and escalates rather than deciding.
 */
@Slf4j
@Component
public class ReportDeliveryIssueTool implements SupportTool {

    private static final List<String> ISSUE_TYPES = List.of(
            "STORE_CLOSED",
            "ORDER_NOT_READY",
            "CUSTOMER_UNREACHABLE",
            "WRONG_ADDRESS",
            "VEHICLE_PROBLEM",
            "SAFETY_CONCERN",
            "OTHER");

    private final SupportOrderAccess orderAccess;

    public ReportDeliveryIssueTool(SupportOrderAccess orderAccess) {
        this.orderAccess = orderAccess;
    }

    @Override
    public String name() {
        return "reportDeliveryIssue";
    }

    @Override
    public String description() {
        return "Report a problem blocking a delivery, such as a closed store, an order that is not "
                + "ready, an unreachable customer or a safety concern. This notifies a human "
                + "dispatcher, so gather the issue type and a short description first.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issueType", Map.of(
                                "type", "string",
                                "enum", ISSUE_TYPES,
                                "description", "The category that fits best."),
                        "details", Map.of(
                                "type", "string",
                                "description", "What happened, in the driver's words."),
                        "orderId", Map.of(
                                "type", "string",
                                "description", "Order id. Omit to use the active delivery.")),
                "required", List.of("issueType", "details"));
    }

    @Override
    public Set<ChatRole> allowedRoles() {
        return Set.of(ChatRole.DRIVER);
    }

    @Override
    public String activityKey() {
        return "support.activity.reportingIssue";
    }

    @Override
    public Map<String, Object> execute(SupportToolContext context, JsonNode arguments) {
        String issueType = ToolArgs.string(arguments, "issueType", "OTHER");
        String details = ToolArgs.string(arguments, "details", "No details given");
        String requestedOrderId = ToolArgs.string(arguments, "orderId", context.thread().getOrderId());

        Optional<Order> order = requestedOrderId != null
                ? orderAccess.findOwned(requestedOrderId, context.principal())
                : orderAccess.recentOrders(context.principal(), 10).stream()
                        .filter(orderAccess::isActive)
                        .findFirst();

        SupportThread thread = context.thread();
        order.ifPresent(value -> thread.setOrderId(value.getOrderId()));
        thread.setState(SupportThreadState.WAITING_HUMAN.name());
        thread.setEscalatedAt(System.currentTimeMillis());
        thread.setEscalationReason(issueType + ": " + details);

        log.warn("Driver {} reported {} on order {}: {}",
                context.principal().id(), issueType,
                order.map(Order::getOrderId).orElse("none"), details);

        return Map.of(
                "reported", true,
                "issueType", issueType,
                "orderId", order.map(Order::getOrderId).orElse("none"),
                "instruction", "Confirm the report is filed and a dispatcher will respond in this "
                        + "conversation. If the issue is a safety concern, tell the driver to "
                        + "prioritise their own safety and to call emergency services if needed.");
    }
}
