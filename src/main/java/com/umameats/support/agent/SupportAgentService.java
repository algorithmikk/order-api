package com.umameats.support.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umameats.chat.model.ChatPrincipal;
import com.umameats.support.config.SupportProperties;
import com.umameats.support.llm.LlmGatewayClient;
import com.umameats.support.llm.LlmMessage;
import com.umameats.support.llm.LlmToolCall;
import com.umameats.support.llm.LlmTurn;
import com.umameats.support.model.SupportMessage;
import com.umameats.support.model.SupportThread;
import com.umameats.support.model.SupportThreadState;
import com.umameats.support.repository.SupportMessageRepository;
import com.umameats.support.repository.SupportThreadRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The agent loop: prompt, stream, run tools, stream again, persist.
 *
 * <p>The loop is bounded. A model that keeps calling tools without converging is
 * cut off after a fixed number of iterations and asked to answer with what it
 * has, which caps both latency and spend on a pathological turn.
 */
@Slf4j
@Service
public class SupportAgentService {

    private static final String SENDER_USER = "USER";
    private static final String SENDER_AGENT = "AGENT";

    /** Longer or later-stage turns get the stronger model. */
    private static final int COMPLEX_MESSAGE_LENGTH = 280;
    private static final int COMPLEX_HISTORY_SIZE = 6;

    private final LlmGatewayClient llmGatewayClient;
    private final SupportToolRegistry toolRegistry;
    private final SupportPromptBuilder promptBuilder;
    private final SupportMessageRepository messageRepository;
    private final SupportThreadRepository threadRepository;
    private final SupportProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SupportAgentService(
            LlmGatewayClient llmGatewayClient,
            SupportToolRegistry toolRegistry,
            SupportPromptBuilder promptBuilder,
            SupportMessageRepository messageRepository,
            SupportThreadRepository threadRepository,
            SupportProperties properties) {
        this.llmGatewayClient = llmGatewayClient;
        this.toolRegistry = toolRegistry;
        this.promptBuilder = promptBuilder;
        this.messageRepository = messageRepository;
        this.threadRepository = threadRepository;
        this.properties = properties;
    }

    /**
     * Persists the user's message, then streams the agent's reply into {@code sink}.
     *
     * <p>Runs synchronously on the request thread: the caller is an SSE endpoint
     * that must stay open for the duration anyway, and order-api runs on virtual
     * threads, so a blocking wait here costs a stack rather than a platform thread.
     */
    public void respond(
            SupportThread thread,
            ChatPrincipal principal,
            String userMessageBody,
            SupportStreamSink sink) {

        persistMessage(thread.getThreadId(), SENDER_USER, userMessageBody, null);

        List<LlmMessage> conversation = new ArrayList<>();
        conversation.add(LlmMessage.system(promptBuilder.build(principal, thread)));
        conversation.addAll(history(thread));

        SupportToolContext toolContext = new SupportToolContext(principal, thread);
        Set<String> toolsUsed = new LinkedHashSet<>();
        StringBuilder answer = new StringBuilder();
        String model = selectModel(userMessageBody, conversation.size());

        try {
            for (int iteration = 0; iteration < properties.getAgent().getMaxToolIterations(); iteration++) {
                boolean lastIteration = iteration == properties.getAgent().getMaxToolIterations() - 1;

                LlmTurn turn = llmGatewayClient.streamTurn(
                        model,
                        conversation,
                        // On the final pass tools are withheld so the model has no
                        // option but to answer with what it already gathered.
                        lastIteration ? List.of() : toolRegistry.definitionsFor(principal.role()),
                        delta -> {
                            answer.append(delta);
                            sink.content(delta);
                        });

                if (!turn.hasToolCalls()) {
                    break;
                }

                conversation.add(LlmMessage.assistantToolCalls(turn.content(), turn.toolCalls()));
                for (LlmToolCall call : turn.toolCalls()) {
                    conversation.add(runTool(call, toolContext, principal, toolsUsed, sink));
                }
            }
        } catch (Exception e) {
            log.error("Support turn failed for thread {}: {}", thread.getThreadId(), e.getMessage());
            if (answer.isEmpty()) {
                sink.failed("The assistant is unavailable right now. Please try again in a moment.");
                return;
            }
        }

        String body = answer.toString().trim();
        if (body.isEmpty()) {
            body = "I could not put together an answer for that. Let me get a person to help you.";
            sink.content(body);
            escalate(thread, "Agent produced an empty response");
        }

        SupportMessage saved = persistMessage(
                thread.getThreadId(),
                SENDER_AGENT,
                body,
                toolsUsed.isEmpty() ? null : String.join(",", toolsUsed));

        touch(thread);
        sink.completed(saved, SupportThreadState.WAITING_HUMAN.name().equals(thread.getState()));
    }

    private LlmMessage runTool(
            LlmToolCall call,
            SupportToolContext context,
            ChatPrincipal principal,
            Set<String> toolsUsed,
            SupportStreamSink sink) {

        Optional<SupportTool> tool = toolRegistry.find(call.name(), principal.role());
        if (tool.isEmpty()) {
            // Either a hallucinated name or a tool this role may not use. Telling
            // the model plainly is better than failing the turn.
            log.warn("Rejected tool '{}' for role {}", call.name(), principal.role());
            return LlmMessage.toolResult(call.id(),
                    "{\"error\":\"No such tool is available to you. Answer without it or escalate.\"}");
        }

        sink.toolStarted(tool.get().name(), tool.get().activityKey());
        toolsUsed.add(tool.get().name());

        try {
            JsonNode arguments = objectMapper.readTree(
                    call.arguments() == null || call.arguments().isBlank() ? "{}" : call.arguments());
            Map<String, Object> result = tool.get().execute(context, arguments);
            return LlmMessage.toolResult(call.id(), objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            log.error("Tool {} failed: {}", call.name(), e.getMessage(), e);
            return LlmMessage.toolResult(call.id(),
                    "{\"error\":\"That lookup failed. Apologise briefly and offer a human agent.\"}");
        }
    }

    /**
     * Cheap complexity heuristic: a long question, or a conversation that has
     * already gone several turns, is worth the stronger model. Most turns are
     * neither, so the cheap model handles the bulk of the traffic.
     */
    private String selectModel(String userMessageBody, int conversationSize) {
        boolean complex = (userMessageBody != null && userMessageBody.length() > COMPLEX_MESSAGE_LENGTH)
                || conversationSize > COMPLEX_HISTORY_SIZE;
        return complex ? properties.getLlm().getEscalationModel() : properties.getLlm().getModel();
    }

    private List<LlmMessage> history(SupportThread thread) {
        return messageRepository
                .findRecent(thread.getThreadId(), properties.getAgent().getHistoryWindow())
                .stream()
                .map(message -> SENDER_USER.equals(message.getSender())
                        ? LlmMessage.user(message.getBody())
                        : LlmMessage.assistant(message.getBody()))
                .toList();
    }

    private void escalate(SupportThread thread, String reason) {
        thread.setState(SupportThreadState.WAITING_HUMAN.name());
        thread.setEscalatedAt(System.currentTimeMillis());
        thread.setEscalationReason(reason);
    }

    private void touch(SupportThread thread) {
        thread.setUpdatedAt(System.currentTimeMillis());
        threadRepository.save(thread);
    }

    private SupportMessage persistMessage(String threadId, String sender, String body, String toolTrace) {
        SupportMessage message = new SupportMessage();
        message.setThreadId(threadId);
        message.setMessageId(System.currentTimeMillis() + "#" + UUID.randomUUID());
        message.setSender(sender);
        message.setBody(body);
        message.setToolTrace(toolTrace);
        message.setCreatedAt(System.currentTimeMillis());
        return messageRepository.save(message);
    }
}
