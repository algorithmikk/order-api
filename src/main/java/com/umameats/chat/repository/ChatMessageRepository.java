package com.umameats.chat.repository;

import com.umameats.chat.model.ChatMessage;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ChatMessageRepository {

    private static final String TABLE_NAME = "umameats-delivery-chat";
    private static final int MAX_LIMIT = 100;

    private final DynamoDbTable<ChatMessage> table;

    public ChatMessageRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(ChatMessage.class));
    }

    public ChatMessage save(ChatMessage message) {
        table.putItem(message);
        return message;
    }

    /**
     * The last {@code limit} messages of an order's conversation, oldest first.
     *
     * <p>Dynamo is queried newest-first so the limit drops the oldest messages
     * rather than the newest, then the page is reversed for display.
     */
    public List<ChatMessage> findByOrderId(String orderId, int limit) {
        int cappedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        List<ChatMessage> newestFirst = table.query(QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(
                                Key.builder().partitionValue(orderId).build()))
                        .scanIndexForward(false)
                        .limit(cappedLimit)
                        .build())
                .stream()
                .flatMap(page -> page.items().stream())
                .limit(cappedLimit)
                .collect(Collectors.toList());

        List<ChatMessage> chronological = new ArrayList<>(newestFirst);
        chronological.sort(Comparator.comparing(ChatMessage::getMessageId));
        return chronological;
    }
}
