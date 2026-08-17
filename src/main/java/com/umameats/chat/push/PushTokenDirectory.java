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
 * Looks up Expo tokens, live-activity tokens, and notification prefs.
 */
@Slf4j
@Component
public class PushTokenDirectory {

    private static final String CUSTOMERS_TABLE = "umameats-customers";
    private static final String DRIVERS_TABLE = "umameats-drivers";

    public record DevicePush(
            String expoPushToken,
            String pushPlatform,
            boolean orderUpdates,
            boolean driverMessages,
            boolean newDelivery,
            boolean deliveryUpdates,
            String liveActivityPushToStartToken,
            String liveActivityUpdateToken,
            String liveActivityOrderId) {
        public boolean hasExpoToken() {
            return expoPushToken != null && !expoPushToken.isBlank();
        }
    }

    private final DynamoDbClient dynamoDbClient;

    public PushTokenDirectory(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public Optional<String> findCustomerToken(String customerId) {
        return findCustomer(customerId).map(DevicePush::expoPushToken).filter(t -> t != null && !t.isBlank());
    }

    public Optional<String> findDriverToken(String driverId) {
        return findDriver(driverId).map(DevicePush::expoPushToken).filter(t -> t != null && !t.isBlank());
    }

    public Optional<DevicePush> findCustomer(String customerId) {
        return findItem(CUSTOMERS_TABLE, "customerId", customerId).map(PushTokenDirectory::fromCustomerItem);
    }

    public Optional<DevicePush> findDriver(String driverId) {
        return findItem(DRIVERS_TABLE, "driverId", driverId).map(PushTokenDirectory::fromDriverItem);
    }

    private Optional<Map<String, AttributeValue>> findItem(String table, String keyName, String keyValue) {
        if (keyValue == null || keyValue.isBlank()) {
            return Optional.empty();
        }
        try {
            GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(table)
                    .key(Map.of(keyName, AttributeValue.fromS(keyValue)))
                    .build());
            if (!response.hasItem() || response.item().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.item());
        } catch (Exception e) {
            log.warn("Could not read push record from {} for {}: {}", table, keyValue, e.getMessage());
            return Optional.empty();
        }
    }

    private static DevicePush fromCustomerItem(Map<String, AttributeValue> item) {
        Map<String, AttributeValue> prefs = mapAttr(item.get("notificationPreferences"));
        return new DevicePush(
                stringAttr(item.get("expoPushToken")),
                stringAttr(item.get("pushPlatform")),
                boolAttr(prefs.get("orderUpdates"), true) && boolAttr(prefs.get("pushNotifications"), true),
                boolAttr(prefs.get("driverMessages"), true),
                true,
                true,
                stringAttr(item.get("liveActivityPushToStartToken")),
                stringAttr(item.get("liveActivityUpdateToken")),
                stringAttr(item.get("liveActivityOrderId")));
    }

    private static DevicePush fromDriverItem(Map<String, AttributeValue> item) {
        return new DevicePush(
                stringAttr(item.get("expoPushToken")),
                stringAttr(item.get("pushPlatform")),
                true,
                true,
                boolAttr(item.get("pushNewDelivery"), true),
                boolAttr(item.get("pushDeliveryUpdates"), true),
                stringAttr(item.get("liveActivityPushToStartToken")),
                stringAttr(item.get("liveActivityUpdateToken")),
                stringAttr(item.get("liveActivityOrderId")));
    }

    private static String stringAttr(AttributeValue value) {
        return value == null || value.s() == null || value.s().isBlank() ? null : value.s();
    }

    private static boolean boolAttr(AttributeValue value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value.bool() != null) {
            return value.bool();
        }
        if (value.s() != null) {
            return Boolean.parseBoolean(value.s());
        }
        return defaultValue;
    }

    private static Map<String, AttributeValue> mapAttr(AttributeValue value) {
        if (value == null || value.m() == null) {
            return Map.of();
        }
        return value.m();
    }
}
