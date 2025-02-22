package com.umameats.client;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.umameats.model.TransactionRequest;
import com.umameats.model.TransactionResponse;

import reactor.core.publisher.Mono;

@Component
public class PaymentApiClient {
    private final WebClient webClient;

    public PaymentApiClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https://api.umameats.com/api/v1/payments")
            .build();
    }

    public Mono<TransactionResponse> createTransaction(TransactionRequest request, String customerId) {
        return webClient.post()
            .uri("/transactions")
            .header("X-Customer-Id", customerId)
            .header("X-Trace-Id", UUID.randomUUID().toString())  // Added for tracing
            .bodyValue(request)
            .retrieve()
            .bodyToMono(TransactionResponse.class);
    }
}
