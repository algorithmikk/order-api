package com.umameats.chat.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;
import java.util.Optional;

/**
 * Looks up the Expo push token for a customer or driver.
 *
 * <p>Reads the owning services' tables directly with a projection on the single
 * attribute it needs. The alternative, an HTTP hop to customer-api or driver-api
 * on every chat message, would add latency and a failure mode to a path that is
 * already best-effort. Mapping only one attribute keeps the coupling to the name
 * of that attribute rather than to either service's full schema.
 */
@Slf4j
@Component
public class PushTokenDirectory {

    private static final String CUSTOMERS_TABLE = "umameats-customers";
    private static final String DRIVERS_TABLE = "umameats-drivers";
    private static final String TOKEN_ATTRIBUTE = "expoPushToken";

    private final DynamoDbClient dynamoDbClient;

    public PushTokenDirectory(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public Optional<String> findCustomerToken(String customerId) {
        return findToken(CUSTOMERS_TABLE, "customerId", customerId);
    }

    public Optional<String> findDriverToken(String driverId) {
        return findToken(DRIVERS_TABLE, "driverId", driverId);
    }

    private Optional<String> findToken(String table, String keyName, String keyValue) {
        if (keyValue == null || keyValue.isBlank()) {
            return Optional.empty();
        }

        try {
            GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(table)
                    .key(Map.of(keyName, AttributeValue.fromS(keyValue)))
                    .projectionExpression(TOKEN_ATTRIBUTE)
                    .build());

            if (!response.hasItem()) {
                return Optional.empty();
            }
            AttributeValue token = response.item().get(TOKEN_ATTRIBUTE);
            return token == null || token.s() == null || token.s().isBlank()
                    ? Optional.empty()
                    : Optional.of(token.s());
        } catch (Exception e) {
            // A push is a nice-to-have; never let this break message delivery.
            log.warn("Could not read push token from {} for {}: {}", table, keyValue, e.getMessage());
            return Optional.empty();
        }
    }
}
