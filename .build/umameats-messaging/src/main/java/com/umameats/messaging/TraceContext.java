package com.umameats.messaging;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Propagates trace / correlation IDs across HTTP and Kafka via MDC.
 */
public final class TraceContext {

    private TraceContext() {
    }

    public static String currentTraceId() {
        String existing = MDC.get(MessagingHeaders.MDC_TRACE_ID);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String generated = UUID.randomUUID().toString();
        MDC.put(MessagingHeaders.MDC_TRACE_ID, generated);
        return generated;
    }

    public static String currentCorrelationId() {
        String existing = MDC.get(MessagingHeaders.MDC_CORRELATION_ID);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String traceId = currentTraceId();
        MDC.put(MessagingHeaders.MDC_CORRELATION_ID, traceId);
        return traceId;
    }

    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(MessagingHeaders.MDC_TRACE_ID, traceId);
        }
    }

    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MessagingHeaders.MDC_CORRELATION_ID, correlationId);
        }
    }

    public static void setEventId(String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            MDC.put(MessagingHeaders.MDC_EVENT_ID, eventId);
        }
    }

    public static void setOrderId(String orderId) {
        if (orderId != null && !orderId.isBlank()) {
            MDC.put(MessagingHeaders.MDC_ORDER_ID, orderId);
        }
    }

    public static void clear() {
        MDC.remove(MessagingHeaders.MDC_TRACE_ID);
        MDC.remove(MessagingHeaders.MDC_CORRELATION_ID);
        MDC.remove(MessagingHeaders.MDC_EVENT_ID);
        MDC.remove(MessagingHeaders.MDC_ORDER_ID);
    }
}
