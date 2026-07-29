package com.umameats.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.umameats.model.Order;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@Repository
public class OrderRepository {
    private static final String TABLE_NAME = "umameats-orders";

    private final DynamoDbEnhancedClient enhancedClient;

    public OrderRepository(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
    }

    private DynamoDbTable<Order> getTable() {
        return enhancedClient.table(TABLE_NAME, TableSchema.fromBean(Order.class));
    }

    public Order save(Order order) {
        getTable().putItem(order);
        return order;
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
}
