package com.umameats.support.llm;

import java.util.List;

/**
 * One entry in the model's conversation, in OpenAI chat-completions shape.
 *
 * @param role       system | user | assistant | tool
 * @param content    text, or null on an assistant turn that only called tools
 * @param toolCalls  populated on assistant turns that requested tools
 * @param toolCallId set on tool-result turns, linking back to the request
 */
public record LlmMessage(
        String role,
        String content,
        List<LlmToolCall> toolCalls,
        String toolCallId) {

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, null, null);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content, null, null);
    }

    public static LlmMessage assistantToolCalls(String content, List<LlmToolCall> toolCalls) {
        return new LlmMessage("assistant", content, toolCalls, null);
    }

    public static LlmMessage toolResult(String toolCallId, String content) {
        return new LlmMessage("tool", content, null, toolCallId);
    }
}
