package com.umameats.support.repository;

import com.umameats.support.model.SupportMessage;
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
public class SupportMessageRepository {

    private static final String TABLE_NAME = "umameats-support-chat";

    private final DynamoDbTable<SupportMessage> table;

    public SupportMessageRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(SupportMessage.class));
    }

    public SupportMessage save(SupportMessage message) {
        table.putItem(message);
        return message;
    }

    /**
     * The last {@code limit} messages of a thread in chronological order.
     * Dynamo is queried newest-first so the limit trims the oldest messages,
     * then the page is reversed for display and for prompt history.
     */
    public List<SupportMessage> findRecent(String threadId, int limit) {
        List<SupportMessage> newestFirst = table.query(QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(
                                Key.builder().partitionValue(threadId).build()))
                        .scanIndexForward(false)
                        .limit(limit)
                        .build())
                .stream()
                .flatMap(page -> page.items().stream())
                .limit(limit)
                .collect(Collectors.toList());

        List<SupportMessage> chronological = new ArrayList<>(newestFirst);
        chronological.sort(Comparator.comparing(SupportMessage::getMessageId));
        return chronological;
    }
}
