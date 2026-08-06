package com.umameats.support.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.umameats.chat.model.ChatRole;
import com.umameats.support.agent.SupportTool;
import com.umameats.support.agent.SupportToolContext;
import com.umameats.support.agent.ToolArgs;
import com.umameats.support.model.SupportThread;
import com.umameats.support.model.SupportThreadState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hands the conversation to a person.
 *
 * <p>Knowing when to stop is part of being useful. An agent that keeps trying on
 * a safety complaint or a large refund does more damage than one that escalates.
 */
@Slf4j
@Component
public class EscalateToHumanTool implements SupportTool {

    @Override
    public String name() {
        return "escalateToHuman";
    }

    @Override
    public String description() {
        return "Hand this conversation to a human support agent. Use it when the user asks for a "
                + "person, when the problem involves safety, harassment, an accident or a legal "
                + "matter, when an action you tried was refused, or when you have tried and cannot "
                + "resolve the issue. Tell the user you are doing it and do not promise a "
                + "specific outcome.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "reason", Map.of(
                                "type", "string",
                                "description", "A short handover summary for the human agent.")),
                "required", List.of("reason"));
    }

    @Override
    public Set<ChatRole> allowedRoles() {
        return Set.of(ChatRole.CUSTOMER, ChatRole.DRIVER);
    }

    @Override
    public String activityKey() {
        return "support.activity.escalating";
    }

    @Override
    public Map<String, Object> execute(SupportToolContext context, JsonNode arguments) {
        String reason = ToolArgs.string(arguments, "reason", "User requested a human agent");

        SupportThread thread = context.thread();
        thread.setState(SupportThreadState.WAITING_HUMAN.name());
        thread.setEscalatedAt(System.currentTimeMillis());
        thread.setEscalationReason(reason);

        log.info("Support thread {} escalated by {} {}: {}",
                thread.getThreadId(), context.principal().role(), context.principal().id(), reason);

        return Map.of(
                "escalated", true,
                "instruction", "Confirm to the user that a support specialist has been notified and "
                        + "will reply in this same conversation. Do not give a response time.");
    }
}
