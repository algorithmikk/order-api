package com.umameats.support.controller;

import com.umameats.support.agent.SupportStreamSink;
import com.umameats.support.model.SupportMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

/**
 * Streams a turn to one client over SSE and closes the emitter when it ends.
 *
 * <p>Once a send fails the client is gone, so the sink latches shut rather than
 * throwing on every subsequent delta of a turn that is still running server-side.
 */
@Slf4j
class SseSupportStreamSink implements SupportStreamSink {

    private final SseEmitter emitter;
    private volatile boolean broken;

    SseSupportStreamSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void content(String delta) {
        send("content", Map.of("delta", delta));
    }

    @Override
    public void toolStarted(String toolName, String activityKey) {
        send("tool", Map.of("tool", toolName, "activityKey", activityKey));
    }

    @Override
    public void completed(SupportMessage message, boolean escalated) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", message.getMessageId());
        payload.put("body", message.getBody());
        payload.put("toolTrace", message.getToolTrace());
        payload.put("createdAt", message.getCreatedAt());
        payload.put("escalated", escalated);

        send("done", payload);
        emitter.complete();
    }

    @Override
    public void failed(String message) {
        send("error", Map.of("message", message));
        emitter.complete();
    }

    private void send(String eventName, Object payload) {
        if (broken) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (Exception e) {
            broken = true;
            log.debug("Support stream closed by client: {}", e.getMessage());
        }
    }
}
