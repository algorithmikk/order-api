package com.umameats.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning knobs for the AI support agent.
 *
 * <p>The gateway is any OpenAI-compatible chat-completions endpoint (OpenRouter,
 * Vercel AI Gateway, a self-hosted vLLM server), so the model can be swapped
 * without code changes. Models are open-weight by default, which keeps the door
 * open to moving inference in-house later.
 */
@ConfigurationProperties(prefix = "support")
public class SupportProperties {

    private final Llm llm = new Llm();
    private final Agent agent = new Agent();
    private final Refund refund = new Refund();

    public Llm getLlm() {
        return llm;
    }

    public Agent getAgent() {
        return agent;
    }

    public Refund getRefund() {
        return refund;
    }

    public static class Llm {
        private String baseUrl = "https://openrouter.ai/api/v1";
        private String apiKey = "";
        private String secretName = "prod/llm-gateway";
        private String secretJsonKey = "LLM_GATEWAY_API_KEY";

        /**
         * Default + complex turns for now: free OpenRouter Nemotron Nano.
         * Agent-oriented tool use; swap escalation later when we add richer
         * business knowledge and need a stronger model for hard cases.
         * @see <a href="https://openrouter.ai/collections/free-models">OpenRouter free models</a>
         */
        private String model = "nvidia/nemotron-3-nano-30b-a3b:free";

        /** Same as {@link #model} until we promote complex turns to a larger free model. */
        private String escalationModel = "nvidia/nemotron-3-nano-30b-a3b:free";

        private double temperature = 0.2;
        private int maxTokens = 900;
        private int timeoutSeconds = 60;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getSecretName() {
            return secretName;
        }

        public void setSecretName(String secretName) {
            this.secretName = secretName;
        }

        public String getSecretJsonKey() {
            return secretJsonKey;
        }

        public void setSecretJsonKey(String secretJsonKey) {
            this.secretJsonKey = secretJsonKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getEscalationModel() {
            return escalationModel;
        }

        public void setEscalationModel(String escalationModel) {
            this.escalationModel = escalationModel;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Agent {
        /** Hard stop on the tool loop so a confused model cannot bill us indefinitely. */
        private int maxToolIterations = 5;

        /** How many prior messages are replayed to the model as conversation history. */
        private int historyWindow = 20;

        public int getMaxToolIterations() {
            return maxToolIterations;
        }

        public void setMaxToolIterations(int maxToolIterations) {
            this.maxToolIterations = maxToolIterations;
        }

        public int getHistoryWindow() {
            return historyWindow;
        }

        public void setHistoryWindow(int historyWindow) {
            this.historyWindow = historyWindow;
        }
    }

    public static class Refund {
        /** Above this the agent must hand off to a human instead of refunding. */
        private long maxAutoRefundCents = 2500;

        /** Share of the order total the agent may refund without human approval. */
        private double maxAutoRefundFraction = 0.5;

        public long getMaxAutoRefundCents() {
            return maxAutoRefundCents;
        }

        public void setMaxAutoRefundCents(long maxAutoRefundCents) {
            this.maxAutoRefundCents = maxAutoRefundCents;
        }

        public double getMaxAutoRefundFraction() {
            return maxAutoRefundFraction;
        }

        public void setMaxAutoRefundFraction(double maxAutoRefundFraction) {
            this.maxAutoRefundFraction = maxAutoRefundFraction;
        }
    }
}
