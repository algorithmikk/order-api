package com.umameats.support.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.umameats.chat.model.ChatRole;
import com.umameats.support.agent.SupportTool;
import com.umameats.support.agent.SupportToolContext;
import com.umameats.support.agent.ToolArgs;
import com.umameats.support.knowledge.HelpCenterArticle;
import com.umameats.support.knowledge.HelpCenterIndex;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Grounds policy answers in the real help centre.
 *
 * <p>Without this the model invents plausible-sounding refund windows and fee
 * rules, which is worse than saying nothing because the user acts on them.
 */
@Component
public class SearchHelpCenterTool implements SupportTool {

    private static final int MAX_RESULTS = 3;

    private final HelpCenterIndex helpCenterIndex;

    public SearchHelpCenterTool(HelpCenterIndex helpCenterIndex) {
        this.helpCenterIndex = helpCenterIndex;
    }

    @Override
    public String name() {
        return "searchHelpCenter";
    }

    @Override
    public String description() {
        return "Search UmaMeats policies and help articles. Use this before answering any question "
                + "about fees, refund windows, cancellation rules, substitutions, payouts or account "
                + "settings. Base your answer on what is returned and never invent a policy.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "The user's question, or the key terms from it.")),
                "required", List.of("query"));
    }

    @Override
    public Set<ChatRole> allowedRoles() {
        return Set.of(ChatRole.CUSTOMER, ChatRole.DRIVER);
    }

    @Override
    public String activityKey() {
        return "support.activity.searchingHelp";
    }

    @Override
    public Map<String, Object> execute(SupportToolContext context, JsonNode arguments) {
        String query = ToolArgs.string(arguments, "query");
        if (query == null) {
            return Map.of("error", "A query is required.");
        }

        List<HelpCenterArticle> matches = helpCenterIndex.search(
                query, context.principal().role(), context.principal().locale(), MAX_RESULTS);

        if (matches.isEmpty()) {
            return Map.of(
                    "articles", List.of(),
                    "note", "No help article covers this. Say you are not certain and offer to "
                            + "connect the user with a human agent rather than guessing.");
        }

        return Map.of("articles", matches.stream()
                .map(article -> Map.of("question", article.question(), "answer", article.answer()))
                .toList());
    }
}
