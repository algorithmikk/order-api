package com.umameats.chat.security;

import com.umameats.chat.model.ChatPrincipal;
import com.umameats.chat.model.ChatRole;
import com.umameats.service.SecretsManagerService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Resolves the caller of a chat or support request from the Bearer JWT.
 *
 * <p>customer-api and driver-api both sign HS256 tokens with the shared
 * {@code prod/jwt-secret}, and only the driver token carries {@code role=driver},
 * so a single verification covers both apps. The rest of order-api trusts the
 * {@code X-Customer-Id} header; that is deliberately not good enough here,
 * because these endpoints expose another user's conversation and let the support
 * agent cancel orders and move money.
 */
@Slf4j
@Component
public class ChatIdentityResolver {

    private static final Set<String> DRIVER_ROLES = Set.of("driver", "DRIVER");

    private final SecretKey secretKey;

    public ChatIdentityResolver(SecretsManagerService secretsManagerService) {
        String jwtSecret = secretsManagerService.getJwtSecret();
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws ChatAuthException when the token is missing, malformed or expired
     */
    public ChatPrincipal resolve(HttpServletRequest request) {
        return resolveToken(bearerToken(request), localeOf(request));
    }

    /**
     * SSE connections opened from React Native cannot always set headers on
     * reconnect, so the stream endpoints also accept {@code ?token=}.
     */
    public ChatPrincipal resolve(HttpServletRequest request, String queryToken) {
        String token = (queryToken != null && !queryToken.isBlank())
                ? queryToken.trim()
                : bearerToken(request);
        return resolveToken(token, localeOf(request));
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ChatAuthException("Missing Authorization Bearer token");
        }
        return header.substring(7).trim();
    }

    private ChatPrincipal resolveToken(String token, String locale) {
        if (token == null || token.isEmpty() || token.startsWith("session_")) {
            throw new ChatAuthException("Invalid session. Please log in again.");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("Chat token validation failed: {}", e.getMessage());
            throw new ChatAuthException("Invalid or expired token");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new ChatAuthException("Token is missing a subject");
        }

        Object roleClaim = claims.get("role");
        ChatRole role = roleClaim != null && DRIVER_ROLES.contains(roleClaim.toString())
                ? ChatRole.DRIVER
                : ChatRole.CUSTOMER;

        return new ChatPrincipal(
                subject,
                role,
                asString(claims.get("email")),
                asString(claims.get("firstName")),
                locale);
    }

    /**
     * The apps send their current UI language. Anything unsupported falls back to
     * English rather than letting the model pick a language on its own.
     */
    private String localeOf(HttpServletRequest request) {
        String requested = request.getHeader("X-App-Language");
        if (requested == null || requested.isBlank()) {
            return "en";
        }
        String normalized = requested.trim().toLowerCase().split("[-_]")[0];
        return "fr".equals(normalized) ? "fr" : "en";
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
