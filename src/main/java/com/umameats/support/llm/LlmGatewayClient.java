package com.umameats.support.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umameats.service.SecretsManagerService;
import com.umameats.support.config.SupportProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Talks to any OpenAI-compatible chat-completions endpoint.
 *
 * <p>Deliberately not tied to a vendor SDK: the models we run are open weight, so
 * the same conversation can move between OpenRouter, a Vercel AI Gateway, or a
 * self-hosted vLLM server by changing {@code support.llm.base-url}.
 */
@Slf4j
@Component
public class LlmGatewayClient {

    private static final String DONE_SENTINEL = "[DONE]";

    private final WebClient webClient;
    private final SupportProperties properties;
    private final SecretsManagerService secretsManagerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String cachedApiKey;

    public LlmGatewayClient(
            WebClient.Builder webClientBuilder,
            SupportProperties properties,
            SecretsManagerService secretsManagerService) {
        this.properties = properties;
        this.secretsManagerService = secretsManagerService;
        this.webClient = webClientBuilder
                .baseUrl(properties.getLlm().getBaseUrl())
                .build();
    }

    /**
     * Runs one model turn, invoking {@code onContentDelta} for each content fragment.
     *
     * <p>Reasoning / thinking tokens are disabled and excluded at the gateway. Callers
     * should still treat streamed content as untrusted until the turn finishes (tool
     * calls may accompany planner prose) and pass the final text through
     * {@link com.umameats.support.agent.CustomerFacingReply}.
     *
     * <p>Streaming and tool calling are handled in the same pass rather than
     * running tools with a separate non-streaming call: the model decides mid-turn
     * whether it needs a tool, and a second request would double both latency and
     * token spend.
     *
     * @return the assembled turn, including any tools the model wants run next
     */
    public LlmTurn streamTurn(
            String model,
            List<LlmMessage> messages,
            List<LlmToolDefinition> tools,
            Consumer<String> onContentDelta) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream().map(this::toWireMessage).toList());
        body.put("stream", true);
        body.put("temperature", properties.getLlm().getTemperature());
        body.put("max_tokens", properties.getLlm().getMaxTokens());
        // Customer chat must never receive chain-of-thought. OpenRouter normalises
        // this; chat_template_kwargs covers NVIDIA NIM / Nemotron thinking mode.
        body.put("reasoning", Map.of(
                "effort", "none",
                "exclude", true,
                "enabled", false));
        body.put("chat_template_kwargs", Map.of("enable_thinking", false));
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools.stream().map(this::toWireTool).toList());
            body.put("tool_choice", "auto");
        }

        StringBuilder content = new StringBuilder();
        // Keyed by the index the gateway assigns, because a single tool call's
        // arguments arrive as fragments spread across many chunks.
        Map<Integer, ToolCallAccumulator> toolCalls = new TreeMap<>();
        String[] finishReason = {null};

        Flux<String> chunks = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(properties.getLlm().getTimeoutSeconds()));

        try (Stream<String> stream = chunks.toStream()) {
            for (String chunk : (Iterable<String>) stream::iterator) {
                if (chunk == null || chunk.isBlank() || DONE_SENTINEL.equals(chunk.trim())) {
                    continue;
                }
                consumeChunk(chunk, content, toolCalls, finishReason, onContentDelta);
            }
        } catch (Exception e) {
            // A partial answer is still worth showing; only a completely empty
            // turn is a hard failure the caller must surface.
            log.error("LLM stream failed for model {}: {}", model, e.getMessage());
            if (content.isEmpty() && toolCalls.isEmpty()) {
                throw new LlmGatewayException("The assistant is unavailable right now", e);
            }
        }

        List<LlmToolCall> resolved = toolCalls.values().stream()
                .filter(ToolCallAccumulator::isUsable)
                .map(ToolCallAccumulator::toToolCall)
                .toList();

        return new LlmTurn(content.toString(), resolved, finishReason[0]);
    }

    private void consumeChunk(
            String chunk,
            StringBuilder content,
            Map<Integer, ToolCallAccumulator> toolCalls,
            String[] finishReason,
            Consumer<String> onContentDelta) {

        JsonNode root;
        try {
            root = objectMapper.readTree(chunk);
        } catch (Exception e) {
            log.debug("Skipping unparseable stream chunk: {}", chunk);
            return;
        }

        JsonNode choice = root.path("choices").path(0);
        if (choice.isMissingNode()) {
            return;
        }

        if (choice.hasNonNull("finish_reason")) {
            finishReason[0] = choice.get("finish_reason").asText();
        }

        JsonNode delta = choice.path("delta");

        // Never forward reasoning / thinking fields — only the customer-facing
        // content channel. Some gateways put CoT in reasoning or reasoning_content.
        JsonNode textDelta = delta.get("content");
        if (textDelta != null && textDelta.isTextual() && !textDelta.asText().isEmpty()) {
            String text = textDelta.asText();
            content.append(text);
            onContentDelta.accept(text);
        }

        JsonNode toolCallDeltas = delta.get("tool_calls");
        if (toolCallDeltas != null && toolCallDeltas.isArray()) {
            for (JsonNode toolCallDelta : toolCallDeltas) {
                int index = toolCallDelta.path("index").asInt(0);
                ToolCallAccumulator accumulator =
                        toolCalls.computeIfAbsent(index, key -> new ToolCallAccumulator());

                if (toolCallDelta.hasNonNull("id")) {
                    accumulator.id = toolCallDelta.get("id").asText();
                }
                JsonNode function = toolCallDelta.path("function");
                if (function.hasNonNull("name")) {
                    accumulator.name = function.get("name").asText();
                }
                if (function.hasNonNull("arguments")) {
                    accumulator.arguments.append(function.get("arguments").asText());
                }
            }
        }
    }

    private Map<String, Object> toWireMessage(LlmMessage message) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("role", message.role());
        // The API requires the key even when a tool-calling turn had no prose.
        wire.put("content", message.content() != null ? message.content() : "");

        if (message.toolCallId() != null) {
            wire.put("tool_call_id", message.toolCallId());
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            List<Map<String, Object>> calls = new ArrayList<>();
            for (LlmToolCall call : message.toolCalls()) {
                calls.add(Map.of(
                        "id", call.id(),
                        "type", "function",
                        "function", Map.of("name", call.name(), "arguments", call.arguments())));
            }
            wire.put("tool_calls", calls);
        }
        return wire;
    }

    private Map<String, Object> toWireTool(LlmToolDefinition tool) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", tool.name(),
                        "description", tool.description(),
                        "parameters", tool.parameters()));
    }

    /**
     * Env var first so local development needs no AWS access, then Secrets Manager.
     */
    private String apiKey() {
        if (cachedApiKey != null) {
            return cachedApiKey;
        }
        synchronized (this) {
            if (cachedApiKey != null) {
                return cachedApiKey;
            }

            String configured = properties.getLlm().getApiKey();
            if (configured != null && !configured.isBlank()) {
                cachedApiKey = configured.trim();
                return cachedApiKey;
            }

            String fromSecrets = secretsManagerService.getSecretValue(
                    properties.getLlm().getSecretName(),
                    properties.getLlm().getSecretJsonKey());
            if (fromSecrets == null || fromSecrets.isBlank()) {
                throw new LlmGatewayException(
                        "No LLM gateway API key: set SUPPORT_LLM_API_KEY or the "
                                + properties.getLlm().getSecretName() + " secret");
            }
            cachedApiKey = fromSecrets.trim();
            return cachedApiKey;
        }
    }

    private static final class ToolCallAccumulator {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        boolean isUsable() {
            return name != null && !name.isBlank();
        }

        LlmToolCall toToolCall() {
            String argumentJson = arguments.isEmpty() ? "{}" : arguments.toString();
            return new LlmToolCall(id != null ? id : name, name, argumentJson);
        }
    }
}
