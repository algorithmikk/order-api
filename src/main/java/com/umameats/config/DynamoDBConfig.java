package com.umameats.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDBConfig {

    @Value("${aws.region}")
    private String region;

    /**
     * Upper bound on concurrent DynamoDB connections. Virtual threads removed the
     * servlet-thread ceiling, so this pool is what actually limits in-flight AWS
     * calls; leaving it at the SDK default of 50 turns a traffic spike into a
     * queue.
     */
    @Value("${aws.dynamodb.max-connections:100}")
    private int maxConnections;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(Apache5HttpClient.builder()
                        .maxConnections(maxConnections)
                        .connectionTimeout(Duration.ofSeconds(2))
                        .socketTimeout(Duration.ofSeconds(5))
                        // Shed load instead of parking callers when the pool is full.
                        .connectionAcquisitionTimeout(Duration.ofSeconds(2)))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(Duration.ofSeconds(3))
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .build())
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}
