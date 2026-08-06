package com.umameats.support.agent;

import com.umameats.chat.model.ChatRole;
import com.umameats.support.llm.LlmToolDefinition;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Role-gated tool catalogue.
 *
 * <p>Gating happens twice on purpose: a role only ever sees the tools it may use,
 * and {@link #find} re-checks at call time. The first prevents the model from
 * being tempted; the second means a hallucinated tool name still cannot execute.
 */
@Component
public class SupportToolRegistry {

    private final Map<String, SupportTool> toolsByName;
    private final Map<ChatRole, List<SupportTool>> toolsByRole = new EnumMap<>(ChatRole.class);
    private final Map<ChatRole, List<LlmToolDefinition>> definitionsByRole = new EnumMap<>(ChatRole.class);

    public SupportToolRegistry(List<SupportTool> tools) {
        this.toolsByName = tools.stream()
                .collect(java.util.stream.Collectors.toMap(SupportTool::name, tool -> tool));

        for (ChatRole role : ChatRole.values()) {
            List<SupportTool> allowed = tools.stream()
                    .filter(tool -> tool.allowedRoles().contains(role))
                    .toList();
            toolsByRole.put(role, allowed);
            definitionsByRole.put(role, allowed.stream()
                    .map(tool -> new LlmToolDefinition(tool.name(), tool.description(), tool.parameters()))
                    .toList());
        }
    }

    public List<LlmToolDefinition> definitionsFor(ChatRole role) {
        return definitionsByRole.getOrDefault(role, List.of());
    }

    public List<SupportTool> toolsFor(ChatRole role) {
        return toolsByRole.getOrDefault(role, List.of());
    }

    /** @return the tool, only if it exists and this role is allowed to run it */
    public Optional<SupportTool> find(String name, ChatRole role) {
        return Optional.ofNullable(toolsByName.get(name))
                .filter(tool -> tool.allowedRoles().contains(role));
    }
}
