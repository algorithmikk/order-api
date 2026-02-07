package com.umameats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SecretsManagerService {

    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> secretCache = new ConcurrentHashMap<>();

    public SecretsManagerService() {
        log.info("Initializing SecretsManagerService for order-api");
        this.secretsManagerClient = SecretsManagerClient.create();
    }

    /**
     * Get a secret value by secret name and JSON key.
     * Results are cached in memory.
     */
    public String getSecretValue(String secretName, String jsonKey) {
        String cacheKey = secretName + ":" + jsonKey;
        return secretCache.computeIfAbsent(cacheKey, k -> fetchSecretValue(secretName, jsonKey));
    }

    private String fetchSecretValue(String secretName, String jsonKey) {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            String secretString = response.secretString();

            JsonNode jsonNode = objectMapper.readTree(secretString);
            if (jsonNode.has(jsonKey)) {
                String value = jsonNode.get(jsonKey).asText();
                log.info("Retrieved secret {}/{} successfully", secretName, jsonKey);
                return value;
            }

            log.warn("Secret {} has no field {}", secretName, jsonKey);
            return null;
        } catch (Exception e) {
            log.error("Failed to retrieve secret {}/{}: {}", secretName, jsonKey, e.getMessage());
            return null;
        }
    }
}

