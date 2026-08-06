package com.umameats.chat.web;

import com.umameats.chat.security.ChatAuthException;
import com.umameats.chat.security.ChatForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps chat failures onto the {@code {"error": "..."}} shape both mobile apps
 * already parse in their api clients.
 *
 * <p>Scoped to the chat and support packages so it cannot change the error
 * contract of the existing order endpoints.
 */
@Slf4j
@RestControllerAdvice(basePackages = {"com.umameats.chat", "com.umameats.support"})
public class ChatExceptionHandler {

    @ExceptionHandler(ChatAuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(ChatAuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ChatForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ChatForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("Unhandled chat error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Something went wrong. Please try again."));
    }
}
