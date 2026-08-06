package com.umameats.chat.security;

/** Signals a 401 on the chat and support endpoints. */
public class ChatAuthException extends RuntimeException {
    public ChatAuthException(String message) {
        super(message);
    }
}
