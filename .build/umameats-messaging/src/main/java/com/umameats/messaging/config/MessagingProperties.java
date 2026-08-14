package com.umameats.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "umameats.messaging")
public class MessagingProperties {

    private boolean enabled = true;
    private boolean traceFilterEnabled = true;
    private boolean kafkaResilienceEnabled = true;
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
