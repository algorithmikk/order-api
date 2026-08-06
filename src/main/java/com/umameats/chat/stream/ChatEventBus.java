package com.umameats.chat.stream;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fan-out for realtime chat events.
 *
 * <p>Publishers never know how many nodes there are, which is the whole point of
 * the seam: {@link InMemoryChatEventBus} is correct while each service runs a
 * single Fargate task (the deployment today), and a Redis pub/sub implementation
 * can replace it on scale-out without touching any caller. Clients also poll as a
 * fallback, so the brief two-task window during a rolling deploy cannot lose a
 * message permanently.
 */
public interface ChatEventBus {

    /** Topic for a customer/driver conversation about one order. */
    static String deliveryTopic(String orderId) {
        return "delivery:" + orderId;
    }

    /** Topic for a support conversation. */
    static String supportTopic(String threadId) {
        return "support:" + threadId;
    }

    /**
     * Opens a stream for one subscriber. The emitter is registered until the
     * client disconnects, times out, or errors.
     */
    SseEmitter subscribe(String topic);

    /** Delivers an event to every current subscriber of {@code topic}. */
    void publish(String topic, String eventName, Object payload);
}
