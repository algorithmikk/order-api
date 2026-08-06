package com.umameats.support.llm;

import java.util.Map;

/**
 * A tool advertised to the model.
 *
 * @param name        function name the model will call
 * @param description what it does and when to use it; this is the only thing
 *                    steering the model's choice, so it carries real weight
 * @param parameters  JSON Schema object describing the arguments
 */
public record LlmToolDefinition(String name, String description, Map<String, Object> parameters) {
}
