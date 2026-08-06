package com.umameats.support.agent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Lenient argument reads for model-produced JSON.
 *
 * <p>Models routinely send a number as a string, omit an optional field, or send
 * an explicit null. None of that should fail a tool call, so every accessor
 * degrades to the supplied default.
 */
public final class ToolArgs {

    private ToolArgs() {
    }

    public static String string(JsonNode arguments, String field) {
        return string(arguments, field, null);
    }

    public static String string(JsonNode arguments, String field, String fallback) {
        if (arguments == null) return fallback;
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) return fallback;
        String text = value.asText().trim();
        return text.isEmpty() ? fallback : text;
    }

    public static long number(JsonNode arguments, String field, long fallback) {
        if (arguments == null) return fallback;
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) return fallback;
        if (value.isNumber()) return value.asLong();
        try {
            return Long.parseLong(value.asText().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int integer(JsonNode arguments, String field, int fallback) {
        return (int) number(arguments, field, fallback);
    }
}
