package com.umameats.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "umameats.messaging")
public class MessagingProperties {

    private boolean enabled = true;
    private boolean traceFilterEnabled = true;
    private boolean kafkaResilienceEnabled = true;
    private boolean outboxEnabled = false;
    private boolean idempotencyEnabled = true;
    private String processedEventsTable = "umameats-processed-events";
    private String outboxTable = "umameats-event-outbox";
    private long idempotencyTtlDays = 14;
    private long outboxTtlDays = 30;
    private int outboxMaxAttempts = 10;
    private int outboxBatchSize = 25;
    private long outboxPollIntervalMs = 2000;
    private long[] consumerRetryIntervalsMs = {1000, 2000, 5000, 10000, 30000};
    private String dltSuffix = ".DLT";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTraceFilterEnabled() {
        return traceFilterEnabled;
    }

    public void setTraceFilterEnabled(boolean traceFilterEnabled) {
        this.traceFilterEnabled = traceFilterEnabled;
    }

    public boolean isKafkaResilienceEnabled() {
        return kafkaResilienceEnabled;
    }

    public void setKafkaResilienceEnabled(boolean kafkaResilienceEnabled) {
        this.kafkaResilienceEnabled = kafkaResilienceEnabled;
    }

    public boolean isOutboxEnabled() {
        return outboxEnabled;
    }

    public void setOutboxEnabled(boolean outboxEnabled) {
        this.outboxEnabled = outboxEnabled;
    }

    public boolean isIdempotencyEnabled() {
        return idempotencyEnabled;
    }

    public void setIdempotencyEnabled(boolean idempotencyEnabled) {
        this.idempotencyEnabled = idempotencyEnabled;
    }

    public String getProcessedEventsTable() {
        return processedEventsTable;
    }

    public void setProcessedEventsTable(String processedEventsTable) {
        this.processedEventsTable = processedEventsTable;
    }

    public String getOutboxTable() {
        return outboxTable;
    }

    public void setOutboxTable(String outboxTable) {
        this.outboxTable = outboxTable;
    }

    public long getIdempotencyTtlDays() {
        return idempotencyTtlDays;
    }

    public void setIdempotencyTtlDays(long idempotencyTtlDays) {
        this.idempotencyTtlDays = idempotencyTtlDays;
    }

    public long getOutboxTtlDays() {
        return outboxTtlDays;
    }

    public void setOutboxTtlDays(long outboxTtlDays) {
        this.outboxTtlDays = outboxTtlDays;
    }

    public int getOutboxMaxAttempts() {
        return outboxMaxAttempts;
    }

    public void setOutboxMaxAttempts(int outboxMaxAttempts) {
        this.outboxMaxAttempts = outboxMaxAttempts;
    }

    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    public void setOutboxBatchSize(int outboxBatchSize) {
        this.outboxBatchSize = outboxBatchSize;
    }

    public long getOutboxPollIntervalMs() {
        return outboxPollIntervalMs;
    }

    public void setOutboxPollIntervalMs(long outboxPollIntervalMs) {
        this.outboxPollIntervalMs = outboxPollIntervalMs;
    }

    public long[] getConsumerRetryIntervalsMs() {
        return consumerRetryIntervalsMs;
    }

    public void setConsumerRetryIntervalsMs(long[] consumerRetryIntervalsMs) {
        this.consumerRetryIntervalsMs = consumerRetryIntervalsMs;
    }

    public String getDltSuffix() {
        return dltSuffix;
    }

    public void setDltSuffix(String dltSuffix) {
        this.dltSuffix = dltSuffix;
    }
}
