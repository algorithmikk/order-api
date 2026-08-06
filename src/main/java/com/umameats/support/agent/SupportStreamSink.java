package com.umameats.support.agent;

import com.umameats.support.model.SupportMessage;

/**
 * Where a turn's output goes as it is produced.
 *
 * <p>An interface rather than an SseEmitter so the agent loop stays testable and
 * can later feed a queue for a WhatsApp or web surface without changes.
 */
public interface SupportStreamSink {

    /** A fragment of the answer, to append to what the user already sees. */
    void content(String delta);

    /**
     * A tool started running. The UI turns {@code activityKey} into a localized
     * line like "Checking your order", which is what makes a multi-second tool
     * call feel like work rather than a hang.
     */
    void toolStarted(String toolName, String activityKey);

    /** The turn finished and was persisted. */
    void completed(SupportMessage message, boolean escalated);

    void failed(String message);
}
