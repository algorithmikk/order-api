package com.umameats.client;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.umameats.messaging.TraceContext;
import com.umameats.model.TransactionRequest;
import com.umameats.model.TransactionResponse;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class PaymentApiClient {
    private final WebClient webClient;
    private final WebClient membershipClient;

    public PaymentApiClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https://api.umameats.com/api/v1/payments")
            .build();
        this.membershipClient = webClientBuilder
            .baseUrl("https://api.umameats.com/api/v1/memberships")
            .build();
    }

    public Mono<TransactionResponse> createTransaction(TransactionRequest request, String customerId) {
        return webClient.post()
            .uri("/transactions")
            .header("X-Customer-Id", customerId)
            .header("X-Trace-Id", TraceContext.currentTraceId())
            .bodyValue(request)
            .retrieve()
            .bodyToMono(TransactionResponse.class);
    }

    /**
     * Server-side check: Founding members get $0 delivery. Defaults to false on errors.
     */
    public boolean hasFoundingDeliveryPerk(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return false;
        }
        try {
            Map<?, ?> body = membershipClient.get()
                .uri("/perk")
                .header("X-Customer-Id", customerId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            if (body == null) return false;
            Object flag = body.get("deliveryPerkActive");
            return Boolean.TRUE.equals(flag);
        } catch (Exception e) {
            log.warn("Failed to check founding membership perk for {}: {}", customerId, e.getMessage());
            return false;
        }
    }
}
