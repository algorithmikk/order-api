package com.umameats.support.llm;

import java.util.List;

/**
 * The result of one model turn.
 *
 * @param content      assembled text, already streamed to the client delta by delta
 * @param toolCalls    tools the model wants run before it can answer
 * @param finishReason as reported by the gateway, for diagnostics
 */
public record LlmTurn(String content, List<LlmToolCall> toolCalls, String finishReason) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
