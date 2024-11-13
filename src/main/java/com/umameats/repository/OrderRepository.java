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
        return Optional.ofNullable(dynamoDBMapper.load(Order.class, orderId, customerId));
    }

    public List<Order> findByCustomerId(String customerId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":customerId", new AttributeValue().withS(customerId));

        DynamoDBQueryExpression<Order> queryExpression = new DynamoDBQueryExpression<Order>()
                .withIndexName("customer-date-index")
                .withConsistentRead(false)
                .withKeyConditionExpression("customerId = :customerId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.query(Order.class, queryExpression);
    }

    public List<Order> findByStoreIdAndStatus(String storeId, String status) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":status", new AttributeValue().withS(status));

        DynamoDBQueryExpression<Order> queryExpression = new DynamoDBQueryExpression<Order>()
                .withIndexName("store-status-index")
                .withConsistentRead(false)
                .withKeyConditionExpression("storeId = :storeId and #status = :status")
                .withExpressionAttributeNames(new HashMap<String, String>() {{
                    put("#status", "status");
                }})
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.query(Order.class, queryExpression);
    }
}
