package com.umameats.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.umameats.model.Order;

@Repository
public class OrderRepository {
    private final DynamoDBMapper dynamoDBMapper;

    public OrderRepository(DynamoDBMapper dynamoDBMapper) {
        this.dynamoDBMapper = dynamoDBMapper;
    }

    public Order save(Order order) {
        dynamoDBMapper.save(order);
        return order;
    }

    public Optional<Order> findById(String orderId, String customerId) {
        return Optional.ofNullable(dynamoDBMapper.load(Order.class, orderId));
    }

    public List<Order> findByStoreIdAndStatus(String storeId, String status) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        
        String keyConditionExpression = "storeId = :storeId";
        String filterExpression = null;
        
        // Only add status condition if status is provided
        if (status != null && !status.isEmpty()) {
            eav.put(":status", new AttributeValue().withS(status));
            filterExpression = "status = :status";
        }

        DynamoDBQueryExpression<Order> queryExpression = new DynamoDBQueryExpression<Order>()
                .withIndexName("store-orders-index")
                .withConsistentRead(false)
                .withKeyConditionExpression(keyConditionExpression)
                .withExpressionAttributeValues(eav);

        if (filterExpression != null) {
            queryExpression.withFilterExpression(filterExpression);
        }

        return dynamoDBMapper.query(Order.class, queryExpression);
    }

    public List<Order> findByStoreId(String storeId) {
        return findByStoreIdAndStatus(storeId, null);
    }

    public List<Order> findByCustomerId(String customerId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":customerId", new AttributeValue().withS(customerId));

        DynamoDBQueryExpression<Order> queryExpression = new DynamoDBQueryExpression<Order>()
                .withIndexName("customer-orders-index")
                .withConsistentRead(false)
                .withKeyConditionExpression("customerId = :customerId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.query(Order.class, queryExpression);
    }
}