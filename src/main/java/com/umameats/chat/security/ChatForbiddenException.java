package com.umameats.chat.security;

/** Signals a 403: the caller is authenticated but not a participant in this thread. */
public class ChatForbiddenException extends RuntimeException {
    public ChatForbiddenException(String message) {
        super(message);
    }
}
