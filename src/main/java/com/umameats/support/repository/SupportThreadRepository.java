package com.umameats.support.repository;

import com.umameats.support.model.SupportThread;
import com.umameats.support.model.SupportThreadState;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class SupportThreadRepository {

    private static final String TABLE_NAME = "umameats-support-threads";

    private final DynamoDbTable<SupportThread> table;

    public SupportThreadRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(SupportThread.class));
    }

    public SupportThread save(SupportThread thread) {
        table.putItem(thread);
        return thread;
    }

    public Optional<SupportThread> findById(String threadId) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(threadId).build()));
    }

    /**
     * Most recent threads for a principal, newest first. Backed by the
     * principal GSI so this never degrades into a table scan.
     */
    public List<SupportThread> findByPrincipalId(String principalId, int limit) {
        return table.index("support-principal-index")
                .query(QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(
                                Key.builder().partitionValue(principalId).build()))
                        .scanIndexForward(false)
                        .limit(limit)
                        .build())
                .stream()
                .flatMap(page -> page.items().stream())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * The thread a new message should join, if the user already has one open.
     */
    public Optional<SupportThread> findOpenThread(String principalId) {
        return findByPrincipalId(principalId, 5).stream()
                .filter(thread -> !SupportThreadState.RESOLVED.name().equals(thread.getState()))
                .findFirst();
    }
}
