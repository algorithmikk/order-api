package com.umameats.support.controller;

import com.umameats.chat.model.ChatPrincipal;
import com.umameats.chat.security.ChatIdentityResolver;
import com.umameats.support.agent.SupportAgentService;
import com.umameats.support.model.SupportMessage;
import com.umameats.support.model.SupportThread;
import com.umameats.support.service.SupportThreadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI support conversations for both apps.
 *
 * <p>{@code /api/v1/support*} is the one new listener rule this work needs; it
 * points at the target group order-api already serves.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/support")
public class SupportChatController {

    /** Held open for the full turn, which can span several tool calls. */
    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final SupportThreadService threadService;
    private final SupportAgentService agentService;
    private final ChatIdentityResolver identityResolver;

    /**
     * A turn blocks on the model, so it cannot run on the request thread: Spring
     * MVC needs the emitter returned before any events are written. Virtual
     * threads make one-per-turn cheap.
     */
    private final ExecutorService agentExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public SupportChatController(
            SupportThreadService threadService,
            SupportAgentService agentService,
            ChatIdentityResolver identityResolver) {
        this.threadService = threadService;
        this.agentService = agentService;
        this.identityResolver = identityResolver;
    }

    /**
     * The user's current conversation and its history, creating one if needed.
     * This is the single call a support screen makes on open.
     */
    @GetMapping("/thread")
    public ResponseEntity<Map<String, Object>> getThread(
            @RequestParam(required = false) String orderId,
            HttpServletRequest request) {
        ChatPrincipal principal = identityResolver.resolve(request);
        SupportThread thread = threadService.getOrCreateThread(principal, orderId);
        return ResponseEntity.ok(describe(thread, threadService.listMessages(thread)));
    }

    @GetMapping("/threads/{threadId}/messages")
    public ResponseEntity<Map<String, Object>> listMessages(
            @PathVariable String threadId,
            HttpServletRequest request) {
        ChatPrincipal principal = identityResolver.resolve(request);
        SupportThread thread = threadService.requireOwnedThread(threadId, principal);
        return ResponseEntity.ok(describe(thread, threadService.listMessages(thread)));
    }

    /**
     * Sends a message and streams the reply.
     *
     * <p>Events: {@code content} for each text fragment, {@code tool} when the
     * agent starts a lookup, {@code done} with the persisted message, and
     * {@code error} if the turn could not complete.
     */
    @PostMapping(path = "/threads/{threadId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @PathVariable String threadId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        ChatPrincipal principal = identityResolver.resolve(request);
        SupportThread thread = threadService.requireOwnedThread(threadId, principal);

        String messageBody = body.get("body");
        if (messageBody == null || messageBody.isBlank()) {
            throw new IllegalArgumentException("Message body is required");
        }

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        SseSupportStreamSink sink = new SseSupportStreamSink(emitter);

        agentExecutor.submit(() -> {
            try {
                agentService.respond(thread, principal, messageBody.trim(), sink);
            } catch (Exception e) {
                log.error("Support turn crashed for thread {}", threadId, e);
                sink.failed("Something went wrong. Please try again.");
            }
        });

        return emitter;
    }

    /** Direct handover, for the "talk to a human" button. */
    @PostMapping("/threads/{threadId}/escalate")
    public ResponseEntity<Map<String, Object>> escalate(
            @PathVariable String threadId,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {

        ChatPrincipal principal = identityResolver.resolve(request);
        SupportThread thread = threadService.requireOwnedThread(threadId, principal);
        String reason = body != null && body.get("reason") != null
                ? body.get("reason")
                : "User asked for a human agent";

        threadService.escalate(thread, reason);
        return ResponseEntity.ok(describe(thread, threadService.listMessages(thread)));
    }

    /** Closes the conversation so the next question starts fresh. */
    @PostMapping("/threads/{threadId}/resolve")
    public ResponseEntity<Map<String, Object>> resolve(
            @PathVariable String threadId,
            HttpServletRequest request) {
        ChatPrincipal principal = identityResolver.resolve(request);
        SupportThread thread = threadService.requireOwnedThread(threadId, principal);
        threadService.resolve(thread);
        return ResponseEntity.ok(Map.of("threadId", thread.getThreadId(), "state", thread.getState()));
    }

    private Map<String, Object> describe(SupportThread thread, List<SupportMessage> messages) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("threadId", thread.getThreadId());
        payload.put("state", thread.getState());
        payload.put("orderId", thread.getOrderId());
        payload.put("escalated", thread.getEscalatedAt() != null);
        payload.put("messages", messages);
        return payload;
    }
}
