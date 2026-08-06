package com.umameats.support.llm;

/**
 * A tool invocation requested by the model.
 *
 * @param id        correlation id the tool result must echo back
 * @param name      tool name
 * @param arguments raw JSON object as a string, exactly as the model emitted it
 */
public record LlmToolCall(String id, String name, String arguments) {
}
