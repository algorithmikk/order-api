package com.umameats.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umameats.messaging.KafkaProducerSupport;
import com.umameats.messaging.consumer.IdempotentEventProcessor;
import com.umameats.messaging.idempotency.DynamoIdempotencyStore;
import com.umameats.messaging.idempotency.IdempotencyStore;
import com.umameats.messaging.idempotency.InMemoryIdempotencyStore;
import com.umameats.messaging.interceptor.TraceRecordInterceptor;
import com.umameats.messaging.outbox.OutboxPublisher;
import com.umameats.messaging.outbox.OutboxWriter;
import com.umameats.messaging.web.TraceIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

@AutoConfiguration(after = KafkaAutoConfiguration.class)
@EnableConfigurationProperties(MessagingProperties.class)
@ConditionalOnProperty(prefix = "umameats.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    public KafkaProducerSupport kafkaProducerSupport(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        return new KafkaProducerSupport(kafkaTemplate, objectMapper, meterRegistry);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(
            prefix = "umameats.messaging",
            name = "trace-filter-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter());
        registration.setOrder(Integer.MIN_VALUE + 100);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceRecordInterceptor traceRecordInterceptor() {
        return new TraceRecordInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    @ConditionalOnBean(DynamoDbEnhancedClient.class)
    @ConditionalOnProperty(
            prefix = "umameats.messaging",
            name = "idempotency-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public IdempotencyStore dynamoIdempotencyStore(
            DynamoDbEnhancedClient enhancedClient, MessagingProperties properties) {
        return new DynamoIdempotencyStore(
                enhancedClient, properties.getProcessedEventsTable(), properties.getIdempotencyTtlDays());
    }

    @Bean
    @ConditionalOnMissingBean({IdempotencyStore.class, DynamoDbEnhancedClient.class})
    @ConditionalOnProperty(
            prefix = "umameats.messaging",
            name = "idempotency-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public IdempotencyStore inMemoryIdempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    @Bean
    @ConditionalOnBean(IdempotencyStore.class)
    @ConditionalOnMissingBean(IdempotentEventProcessor.class)
    public IdempotentEventProcessor idempotentEventProcessor(
            IdempotencyStore idempotencyStore, MeterRegistry meterRegistry) {
        return new IdempotentEventProcessor(idempotencyStore, meterRegistry);
    }

    @Bean
    @ConditionalOnBean(DynamoDbEnhancedClient.class)
    @ConditionalOnProperty(prefix = "umameats.messaging", name = "outbox-enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public OutboxWriter outboxWriter(
            DynamoDbEnhancedClient enhancedClient,
            ObjectMapper objectMapper,
            MessagingProperties properties) {
        return new OutboxWriter(
                enhancedClient, objectMapper, properties.getOutboxTable(), properties.getOutboxTtlDays());
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnBean(OutboxWriter.class)
    @ConditionalOnProperty(prefix = "umameats.messaging", name = "outbox-enabled", havingValue = "true")
    static class OutboxSchedulingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public OutboxPublisher outboxPublisher(
                OutboxWriter outboxWriter,
                KafkaProducerSupport kafkaProducerSupport,
                MeterRegistry meterRegistry,
                MessagingProperties properties) {
            return new OutboxPublisher(
                    outboxWriter,
                    kafkaProducerSupport,
                    meterRegistry,
                    properties.getOutboxMaxAttempts(),
                    properties.getOutboxBatchSize());
        }
    }

    @Bean
    @ConditionalOnClass(KafkaTemplate.class)
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnProperty(
            prefix = "umameats.messaging",
            name = "kafka-resilience-enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public CommonErrorHandler kafkaCommonErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            MessagingProperties properties,
            MeterRegistry meterRegistry) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (ConsumerRecord<?, ?> record, Exception ex) -> {
                            meterRegistry
                                    .counter("kafka.consume.dlt", "topic", record.topic())
                                    .increment();
                            return new TopicPartition(
                                    record.topic() + properties.getDltSuffix(), record.partition());
                        });

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(30_000L);
        backOff.setMaxElapsedTime(60_000L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setRetryListeners(
                (record, ex, deliveryAttempt) ->
                        meterRegistry
                                .counter("kafka.consume.retry", "topic", record.topic())
                                .increment());
        return errorHandler;
    }

    @Bean
    @ConditionalOnBean(TraceRecordInterceptor.class)
    public ContainerCustomizer<String, String, ConcurrentMessageListenerContainer<String, String>>
            messagingContainerCustomizer(TraceRecordInterceptor traceRecordInterceptor) {
        return container -> {
            container.setRecordInterceptor(traceRecordInterceptor);
            container.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        };
    }
}
