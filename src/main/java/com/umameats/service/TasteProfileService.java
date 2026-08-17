package com.umameats.service;

import com.umameats.model.Order;
import com.umameats.model.OrderItem;
import com.umameats.model.OrderStatus;
import com.umameats.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TasteProfileService {

    static final String TASTE_TABLE = "umameats-user-taste";
    static final String STORES_TABLE = "umameats-stores";

    private final OrderRepository orderRepository;
    private final DynamoDbClient dynamoDbClient;

    public void rebuildAsync(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                rebuild(customerId);
            } catch (Exception e) {
                log.warn("[Taste] rebuild failed customer={}: {}", customerId, e.getMessage());
            }
        });
    }

    public void rebuild(String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId).stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .toList();
        Map<String, Integer> storeCounts = new HashMap<>();
        Map<String, Integer> itemCounts = new HashMap<>();
        List<double[]> vectors = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        int i = 0;
        for (Order order : orders) {
            double weight = 1.0 / (1.0 + i);
            i++;
            if (order.getStoreId() != null) {
                storeCounts.merge(order.getStoreId(), 1, Integer::sum);
            }
            if (order.getItems() == null) {
                continue;
            }
            for (OrderItem item : order.getItems()) {
                if (item.getItemName() != null) {
                    itemCounts.merge(item.getItemName(), 1, Integer::sum);
                }
                List<Double> embedding = loadDishEmbedding(order.getStoreId(), item.getItemId());
                if (embedding != null && !embedding.isEmpty()) {
                    vectors.add(embedding.stream().mapToDouble(Double::doubleValue).toArray());
                    weights.add(weight);
                }
            }
        }
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("customerId", AttributeValue.fromS(customerId));
        item.put("updatedAt", AttributeValue.fromN(Long.toString(System.currentTimeMillis())));
        item.put("reorderStoreCounts", AttributeValue.fromM(toNumMap(storeCounts)));
        item.put("reorderItemCounts", AttributeValue.fromM(toNumMap(itemCounts)));
        List<String> topCuisines = itemCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
        if (!topCuisines.isEmpty()) {
            item.put("topCuisines", AttributeValue.fromL(topCuisines.stream().map(AttributeValue::fromS).toList()));
        }
        List<Double> mean = weightedMean(vectors, weights);
        if (!mean.isEmpty()) {
            item.put("tasteEmbedding", AttributeValue.fromL(mean.stream()
                    .map(v -> AttributeValue.fromN(Double.toString(v)))
                    .toList()));
        }
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(TASTE_TABLE).item(item).build());
    }

    private List<Double> loadDishEmbedding(String storeId, String itemId) {
        if (storeId == null || itemId == null || itemId.isBlank()) {
            return List.of();
        }
        try {
            var row = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(STORES_TABLE)
                    .key(Map.of(
                            "PK", AttributeValue.fromS("STORE#" + storeId),
                            "SK", AttributeValue.fromS("DISH#" + itemId)))
                    .projectionExpression("searchEmbedding")
                    .build()).item();
            if (row == null || !row.containsKey("searchEmbedding") || row.get("searchEmbedding").l() == null) {
                return List.of();
            }
            List<Double> out = new ArrayList<>();
            for (AttributeValue n : row.get("searchEmbedding").l()) {
                if (n.n() != null) {
                    out.add(Double.parseDouble(n.n()));
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Map<String, AttributeValue> toNumMap(Map<String, Integer> counts) {
        Map<String, AttributeValue> out = new HashMap<>();
        counts.forEach((k, v) -> out.put(k, AttributeValue.fromN(Integer.toString(v))));
        return out;
    }

    private static List<Double> weightedMean(List<double[]> vectors, List<Double> weights) {
        if (vectors.isEmpty()) {
            return List.of();
        }
        int dim = vectors.get(0).length;
        double[] acc = new double[dim];
        double wsum = 0;
        for (int i = 0; i < vectors.size(); i++) {
            double w = weights.get(i);
            wsum += w;
            double[] v = vectors.get(i);
            if (v.length != dim) {
                continue;
            }
            for (int d = 0; d < dim; d++) {
                acc[d] += v[d] * w;
            }
        }
        if (wsum == 0) {
            return List.of();
        }
        List<Double> out = new ArrayList<>(dim);
        double norm = 0;
        for (int d = 0; d < dim; d++) {
            acc[d] /= wsum;
            norm += acc[d] * acc[d];
        }
        norm = Math.sqrt(norm);
        for (int d = 0; d < dim; d++) {
            out.add(norm == 0 ? acc[d] : acc[d] / norm);
        }
        return out;
    }
}
