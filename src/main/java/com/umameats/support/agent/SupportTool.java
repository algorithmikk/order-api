package com.umameats.support.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.umameats.chat.model.ChatRole;

import java.util.Map;
import java.util.Set;

/**
 * A capability the support agent can invoke.
 *
 * <p>Tools are what make the assistant useful. A model with no tools can only
 * paraphrase a help page; a model that can read the actual order answers
 * "where is my food" correctly.
 */
public interface SupportTool {

    /** Function name exposed to the model. */
    String name();

    /**
     * What the tool does and when to reach for it. This is the only signal
     * steering the model's choice, so it should read like an instruction.
     */
    String description();

    /** JSON Schema for the arguments object. */
    Map<String, Object> parameters();

    /** Roles allowed to use this tool; anything else never sees it advertised. */
    Set<ChatRole> allowedRoles();

    /**
     * i18n key for the status the UI shows while this runs, e.g.
     * {@code support.activity.checkingOrder}.
     */
    String activityKey();

    /**
     * @return a JSON-serializable result handed back to the model. Failures should
     *         be returned as data (for example {@code {"error": "..."}}) rather than
     *         thrown, so the model can explain the problem instead of the turn dying.
     */
    Map<String, Object> execute(SupportToolContext context, JsonNode arguments);
}
