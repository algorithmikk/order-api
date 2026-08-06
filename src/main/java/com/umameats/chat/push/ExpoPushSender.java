package com.umameats.chat.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Posts notifications to Expo's push service.
 *
 * <p>Mirrors the batch approach already proven in driver-api's ExpoPushService.
 * Every failure is swallowed and logged: a notification is an enhancement, and
 * an Expo outage must not surface as a failed chat message.
 */
@Slf4j
@Component
public class ExpoPushSender {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public ExpoPushSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param channelId Android notification channel; chat uses its own so users
     *                  can mute delivery offers without muting messages
     */
    public void send(String expoPushToken, String title, String body, String channelId, Map<String, Object> data) {
        if (expoPushToken == null || expoPushToken.isBlank()) {
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("to", expoPushToken);
        message.put("sound", "default");
        message.put("title", title);
        message.put("body", body);
        message.put("priority", "high");
        message.put("channelId", channelId);
        message.put("data", data != null ? data : Map.of());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");

            restTemplate.postForObject(
                    EXPO_PUSH_URL,
                    new HttpEntity<>(objectMapper.writeValueAsString(List.of(message)), headers),
                    String.class);
        } catch (Exception e) {
            log.warn("Expo push send failed: {}", e.getMessage());
        }
    }
}
