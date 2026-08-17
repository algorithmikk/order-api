package com.umameats.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sends ActivityKit Live Activity start/update/end over APNs HTTP/2.
 * Expo Push cannot set apns-push-type=liveactivity.
 *
 * Credentials are optional env (APNS_KEY_ID / APNS_TEAM_ID / APNS_P8). When
 * missing, live APNs is skipped and Expo banners still send.
 */
@Slf4j
@Component
public class LiveActivityApnsClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String keyId;
    private final String teamId;
    private final String p8;
    private final boolean production;
    private final AtomicReference<CachedJwt> jwt = new AtomicReference<>();

    public LiveActivityApnsClient(
            ObjectMapper objectMapper,
            @Value("${apns.key-id:#{null}}") String keyId,
            @Value("${apns.team-id:#{null}}") String teamId,
            @Value("${apns.p8:#{null}}") String p8,
            @Value("${apns.production:true}") boolean production) {
        this.objectMapper = objectMapper;
        this.keyId = keyId;
        this.teamId = teamId;
        this.p8 = p8;
        this.production = production;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isConfigured() {
        return notBlank(keyId) && notBlank(teamId) && notBlank(p8);
    }

    public void start(
            String deviceToken,
            String bundleId,
            String attributesType,
            Map<String, Object> attributes,
            Map<String, Object> contentState,
            String alertTitle,
            String alertBody) {
        if (!isConfigured() || isBlank(deviceToken)) {
            return;
        }
        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("timestamp", Instant.now().getEpochSecond());
        aps.put("event", "start");
        aps.put("attributes-type", attributesType);
        aps.put("attributes", attributes);
        aps.put("content-state", contentState);
        aps.put("alert", Map.of(
                "title", alertTitle != null ? alertTitle : "",
                "body", alertBody != null ? alertBody : ""));
        post(deviceToken, bundleId, aps);
    }

    public void update(String deviceToken, String bundleId, Map<String, Object> contentState) {
        if (!isConfigured() || isBlank(deviceToken)) {
            return;
        }
        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("timestamp", Instant.now().getEpochSecond());
        aps.put("event", "update");
        aps.put("content-state", contentState);
        post(deviceToken, bundleId, aps);
    }

    public void end(String deviceToken, String bundleId, Map<String, Object> contentState) {
        if (!isConfigured() || isBlank(deviceToken)) {
            return;
        }
        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("timestamp", Instant.now().getEpochSecond());
        aps.put("event", "end");
        aps.put("dismissal-date", Instant.now().getEpochSecond());
        aps.put("content-state", contentState != null ? contentState : Map.of());
        post(deviceToken, bundleId, aps);
    }

    private void post(String deviceToken, String bundleId, Map<String, Object> aps) {
        try {
            String host = production ? "https://api.push.apple.com" : "https://api.sandbox.push.apple.com";
            String body = objectMapper.writeValueAsString(Map.of("aps", aps));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(host + "/3/device/" + deviceToken))
                    .timeout(Duration.ofSeconds(8))
                    .header("authorization", "bearer " + token())
                    .header("apns-topic", bundleId + ".push-type.liveactivity")
                    .header("apns-push-type", "liveactivity")
                    .header("apns-priority", "10")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("live.update apns status={} body={}", response.statusCode(), response.body());
            } else {
                log.info("live.update apns event={} bundle={}", aps.get("event"), bundleId);
            }
        } catch (Exception e) {
            log.warn("live.update apns failed: {}", e.getMessage());
        }
    }

    private String token() throws Exception {
        CachedJwt cached = jwt.get();
        if (cached != null && Instant.now().isBefore(cached.expiresAt.minusSeconds(60))) {
            return cached.value;
        }
        PrivateKey key = parseP8(p8);
        Instant now = Instant.now();
        String value = Jwts.builder()
                .header().add("kid", keyId).and()
                .issuer(teamId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(50))))
                .signWith(key, Jwts.SIG.ES256)
                .compact();
        jwt.set(new CachedJwt(value, now.plus(Duration.ofMinutes(50))));
        return value;
    }

    private static PrivateKey parseP8(String pem) throws Exception {
        String sanitized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(sanitized);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedJwt(String value, Instant expiresAt) {
    }
}
