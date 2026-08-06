package com.umameats.support.model;

public enum SupportThreadState {
    /** The agent is handling the conversation. */
    AI,
    /** Escalated: the agent stops answering and an ops agent picks it up. */
    WAITING_HUMAN,
    /** An ops agent is replying. */
    HUMAN,
    RESOLVED
}
