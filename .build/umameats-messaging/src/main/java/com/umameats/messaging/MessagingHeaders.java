package com.umameats.messaging;

/**
 * Standard Kafka header names for UmaMeats event envelopes.
 */
public final class MessagingHeaders {

    public static final String TRACE_ID = "X-Trace-Id";
    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String EVENT_ID = "X-Event-Id";
    public static final String EVENT_TYPE = "X-Event-Type";
    public static final String CAUSATION_ID = "X-Causation-Id";
    public static final String SCHEMA_VERSION = "X-Schema-Version";

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_EVENT_ID = "eventId";
    public static final String MDC_ORDER_ID = "orderId";

    private MessagingHeaders() {
    }
}
