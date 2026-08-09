package com.umameats.messaging.web;

import com.umameats.messaging.MessagingHeaders;
import com.umameats.messaging.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Accepts or generates X-Trace-Id, puts it in MDC, and echoes it on the response.
 */
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String traceId = firstNonBlank(
                    request.getHeader(MessagingHeaders.TRACE_ID),
                    request.getHeader("traceparent"),
                    UUID.randomUUID().toString());
            // W3C traceparent: version-traceid-spanid-flags — use the 32-hex trace id when present
            if (traceId != null && traceId.contains("-") && traceId.split("-").length >= 4) {
                String[] parts = traceId.split("-");
                if (parts[1].length() == 32) {
                    traceId = parts[1];
                }
            }
            TraceContext.setTraceId(traceId);
            TraceContext.setCorrelationId(firstNonBlank(
                    request.getHeader(MessagingHeaders.CORRELATION_ID),
                    traceId));
            response.setHeader(MessagingHeaders.TRACE_ID, TraceContext.currentTraceId());
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
