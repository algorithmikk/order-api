package com.umameats.support.agent;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strips model-internal reasoning so only customer-safe prose is shown or stored.
 *
 * <p>Reasoning models (e.g. Nemotron) often emit chain-of-thought in {@code content}
 * or wrap it in {@code <think>} tags. OpenRouter can exclude reasoning tokens, but
 * some providers still leak narration into the visible reply — this is the last line
 * of defence before SSE and DynamoDB.
 */
public final class CustomerFacingReply {

    private static final Pattern THINK_BLOCK = Pattern.compile(
            "(?is)<\\s*(?:think|thinking|reasoning)\\s*>.*?<\\s*/\\s*(?:think|thinking|reasoning)\\s*>");
    private static final Pattern UNCLOSED_THINK = Pattern.compile(
            "(?is)<\\s*(?:think|thinking|reasoning)\\s*>.*");
    private static final Pattern FINAL_ANSWER_MARKER = Pattern.compile(
            "(?is)(?:^|\\n)\\s*(?:thus\\s+)?(?:the\\s+)?final\\s+answer\\s*[:\\-–—]?\\s*");
    private static final Pattern LEADING_META = Pattern.compile(
            "(?is)^\\s*(?:okay[,.]?|alright[,.]?|let me think[,.]?|thinking[,.]?)\\s+");

    private CustomerFacingReply() {
    }

    /**
     * Returns customer-visible text, or empty when nothing safe remains.
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String text = raw;
        text = THINK_BLOCK.matcher(text).replaceAll("");
        text = UNCLOSED_THINK.matcher(text).replaceAll("");
        text = stripAfterFinalAnswerMarker(text);
        text = LEADING_META.matcher(text).replaceFirst("");
        text = text.trim();

        if (text.isEmpty() || looksLikeInternalMonologue(text)) {
            return "";
        }
        return text;
    }

    /**
     * When the model labels a section "Final answer:", keep only what follows.
     */
    private static String stripAfterFinalAnswerMarker(String text) {
        Matcher matcher = FINAL_ANSWER_MARKER.matcher(text);
        int lastEnd = -1;
        while (matcher.find()) {
            lastEnd = matcher.end();
        }
        if (lastEnd >= 0) {
            return text.substring(lastEnd);
        }
        return text;
    }

    /**
     * Heuristic for CoT that never reached a customer reply (tool-policy quotes,
     * planner voice, etc.). Prefer returning empty so the agent can fall back
     * rather than leaking instructions.
     */
    static boolean looksLikeInternalMonologue(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("according to policy")
                || lower.contains("state the amount in cents")
                || lower.contains("always call this rather than")
                || lower.contains("we need to determine")
                || lower.contains("we need to process that request")
                || lower.contains("we need to handle a request")
                || lower.contains("we have the order id from earlier")
                || lower.contains("handed to a human automatically")) {
            return true;
        }
        // Planner voice without a short customer-facing close.
        if ((lower.startsWith("we need to") || lower.startsWith("i need to"))
                && text.length() > 180) {
            return true;
        }
        return false;
    }
}
