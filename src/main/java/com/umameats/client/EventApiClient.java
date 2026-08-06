package com.umameats.client;


import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.umameats.messaging.TraceContext;
import com.umameats.model.EventRequest;
import com.umameats.model.EventResponse;

import reactor.core.publisher.Mono;

@Component
public class EventApiClient {
    private final WebClient webClient;

    public EventApiClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https://api.umameats.com/api/v1/events")
            .build();
    }

    /**
     * Creates a delivery event for an order
     */
    public Mono<EventResponse> createDeliveryEvent(String orderId, EventRequest eventRequest) {
        return webClient.post()
            .uri("/delivery/{orderId}", orderId)
            .header("X-Trace-Id", TraceContext.currentTraceId())
            .bodyValue(eventRequest)
            .retrieve()
            .bodyToMono(EventResponse.class);
    }
}
