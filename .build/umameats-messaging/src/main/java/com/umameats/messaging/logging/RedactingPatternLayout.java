package com.umameats.messaging.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Redacts common PII / secret patterns from log messages.
 */
public class RedactingPatternLayout extends PatternLayout {

    private static final Pattern[] PATTERNS = {
        Pattern.compile("(?i)((?:password|passwd|secret|token|authorization|api[_-]?key)\\s*[:=]\\s*)[^\\s,;]+"),
        Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-]+"),
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    };

    @Override
    public String doLayout(ILoggingEvent event) {
        String message = super.doLayout(event);
        if (message == null) {
            return null;
        }
        String redacted = message;
        redacted = PATTERNS[0].matcher(redacted).replaceAll("$1***REDACTED***");
        redacted = PATTERNS[1].matcher(redacted).replaceAll("Bearer ***REDACTED***");
        redacted = PATTERNS[2].matcher(redacted).replaceAll("***REDACTED_EMAIL***");
        return redacted;
    }
}
