package com.umameats.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.umameats.model.DeliveryPinAttributeConverter;
import com.umameats.model.Order;
import com.umameats.model.OrderStatus;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

@Repository
public class OrderRepository {
    private static final String TABLE_NAME = "umameats-orders";

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbClient dynamoDbClient;

    public OrderRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbClient dynamoDbClient) {
        this.enhancedClient = enhancedClient;
        this.dynamoDbClient = dynamoDbClient;
    }

    private DynamoDbTable<Order> getTable() {
        return enhancedClient.table(TABLE_NAME, TableSchema.fromBean(Order.class));
    }

    public Order save(Order order) {
        getTable().putItem(order);
        return order;
    }

    /**
     * Sets deliveryPin with a conditional UpdateItem so a customer GET never
     * putItem-clobbers a concurrent driver accept (status, assignment, etc.).
     *
     * @return the PIN that won: the one we wrote, or the PIN already on the item
     */
    public String assignDeliveryPinIfAbsent(String orderId, String pin) {
        try {
            UpdateItemResponse response = dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("orderId", AttributeValue.fromS(orderId)))
                    .updateExpression("SET deliveryPin = :pin")
                    .conditionExpression("attribute_not_exists(deliveryPin) OR deliveryPin = :empty")
                    .expressionAttributeValues(Map.of(
                            ":pin", AttributeValue.fromS(pin),
                            ":empty", AttributeValue.fromS("")))
                    .returnValues(ReturnValue.ALL_NEW)
                    .build());
            return DeliveryPinAttributeConverter.fromAttribute(response.attributes().get("deliveryPin"));
        } catch (ConditionalCheckFailedException e) {
            GetItemResponse existing = dynamoDbClient.getItem(r -> r
                    .tableName(TABLE_NAME)
                    .key(Map.of("orderId", AttributeValue.fromS(orderId)))
                    .projectionExpression("deliveryPin"));
            String stored = DeliveryPinAttributeConverter.fromAttribute(
                    existing.item() != null ? existing.item().get("deliveryPin") : null);
            return !stored.isEmpty() ? stored : pin;
        }
    }

    public Optional<Order> findById(String orderId, String customerId) {
        return Optional.ofNullable(getTable().getItem(Key.builder().partitionValue(orderId).build()));
    }

    public List<Order> findByStoreIdAndStatus(String storeId, String status) {
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(storeId).build()));

        if (status != null && !status.isEmpty()) {
            requestBuilder.filterExpression(Expression.builder()
                    .expression("#status = :status")
                    .putExpressionName("#status", "status")
                    .putExpressionValue(":status", AttributeValue.builder().s(status).build())
                    .build());
        }

        return getTable().index("store-orders-index")
                .query(requestBuilder.build())
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public List<Order> findByStoreId(String storeId) {
        return findByStoreIdAndStatus(storeId, null);
    }

    public List<Order> findByCustomerId(String customerId) {
        return getTable().index("customer-orders-index")
                .query(QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(
                                Key.builder().partitionValue(customerId).build()))
                        .build())
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    /**
     * Orders assigned to a driver, via the driver-orders-index GSI.
     *
     * <p>Older records store the driver's email in {@code driverId}, so callers
     * that have both should query each and merge, the way driver-api does.
     */
    public List<Order> findByDriverId(String driverId) {
        return getTable().index("driver-orders-index")
                .query(QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(
                                Key.builder().partitionValue(driverId).build()))
                        .build())
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    /**
     * Ops / board query: status-index GSI (same index used by driver-api available orders).
     */
    public List<Order> findByStatus(OrderStatus status) {
        return getTable().index("status-index")
                .query(QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(
                                Key.builder().partitionValue(status.name()).build()))
                        .build())
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }
}
