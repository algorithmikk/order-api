package com.umameats.messaging.interceptor;

import com.umameats.messaging.MessagingHeaders;
import com.umameats.messaging.TraceContext;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Restores trace context from Kafka headers into MDC for each consumed record.
 */
public class TraceRecordInterceptor implements RecordInterceptor<String, String> {

    @Override
    public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        TraceContext.setTraceId(headerValue(record, MessagingHeaders.TRACE_ID));
        TraceContext.setCorrelationId(headerValue(record, MessagingHeaders.CORRELATION_ID));
        TraceContext.setEventId(headerValue(record, MessagingHeaders.EVENT_ID));
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        TraceContext.clear();
    }

    @Override
    public void success(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        // no-op
    }

    @Override
    public void failure(ConsumerRecord<String, String> record, Exception exception, Consumer<String, String> consumer) {
        // MDC cleared in afterRecord
    }

    private static String headerValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
