package com.umameats.chat.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Single-node {@link ChatEventBus}, modelled on the proven OfferStreamHub in
 * umameats-driver-api.
 */
@Slf4j
@Component
public class InMemoryChatEventBus implements ChatEventBus {

    /**
     * The ALB drops idle connections at 60s, so the stream has to say something
     * more often than that or every client would reconnect on a timer.
     */
    private static final long HEARTBEAT_INTERVAL_MS = 25_000L;

    /** No server-side timeout; the client owns reconnection. */
    private static final long NO_TIMEOUT = 0L;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByTopic = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String topic) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emittersByTopic.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(topic, emitter));
        emitter.onTimeout(() -> remove(topic, emitter));
        emitter.onError(error -> remove(topic, emitter));

        // An immediate frame tells the client the stream is live, so it can stand
        // its polling fallback down straight away instead of after the first message.
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("topic", topic)));
        } catch (Exception e) {
            remove(topic, emitter);
        }
        return emitter;
    }

    @Override
    public void publish(String topic, String eventName, Object payload) {
        List<SseEmitter> emitters = emittersByTopic.get(topic);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (Exception e) {
                // A dead client is normal (backgrounded app, lost signal); the
                // message is already persisted and will arrive on reconnect.
                remove(topic, emitter);
            }
        }
    }

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    void sendHeartbeats() {
        for (String topic : emittersByTopic.keySet()) {
            publish(topic, "heartbeat", Map.of("at", System.currentTimeMillis()));
        }
    }

    private void remove(String topic, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByTopic.get(topic);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByTopic.remove(topic);
        }
    }
}
