package com.umameats.chat.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umameats.notify.NotificationCatalog;
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
 * Posts notifications to Expo's push service (APNs / FCM).
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

    public void send(String expoPushToken, String title, String body, String channelId, Map<String, Object> data) {
        send(expoPushToken, title, body, channelId, data, "active", null, true);
    }

    public void send(
            String expoPushToken,
            String title,
            String body,
            String channelId,
            Map<String, Object> data,
            String interruptionLevel,
            String collapseId,
            boolean sendBanner) {
        if (expoPushToken == null || expoPushToken.isBlank()) {
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("to", expoPushToken);
        message.put("priority", "high");
        message.put("channelId", channelId != null ? channelId : "orders");
        message.put("data", data != null ? data : Map.of());
        if (collapseId != null && !collapseId.isBlank()) {
            message.put("collapseId", collapseId);
        }
        if (interruptionLevel != null && !interruptionLevel.isBlank()) {
            message.put("interruptionLevel", interruptionLevel);
        }

        if (sendBanner && title != null && !title.isBlank()) {
            message.put("title", title);
            message.put("body", body != null ? body : "");
            message.put("sound", "default");
        } else {
            message.put("_contentAvailable", true);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");

            restTemplate.postForObject(
                    EXPO_PUSH_URL,
                    new HttpEntity<>(objectMapper.writeValueAsString(List.of(message)), headers),
                    String.class);
            log.info("push.sent channel={} banner={} type={}",
                    channelId, sendBanner, data != null ? data.get("type") : null);
        } catch (Exception e) {
            log.warn("Expo push send failed: {}", e.getMessage());
        }
    }

    public void sendCopy(String expoPushToken, NotificationCatalog.Copy copy, Map<String, Object> data, String collapseId) {
        send(
                expoPushToken,
                copy.title,
                copy.body,
                copy.channelId,
                data,
                copy.interruptionLevel,
                collapseId,
                copy.sendBanner);
    }
}
