package com.umameats.client;


import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.umameats.model.EventRequest;
import com.umameats.model.EventResponse;

import reactor.core.publisher.Mono;

import java.util.UUID;

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
     *
     * @param orderId The ID of the order
     * @param eventRequest The event request containing the delivery event
     * @return A Mono containing the event response
     */
    public Mono<EventResponse> createDeliveryEvent(String orderId, EventRequest eventRequest) {
        return webClient.post()
            .uri("/delivery/{orderId}", orderId)
            .header("X-Trace-Id", UUID.randomUUID().toString())  // For tracing
            .bodyValue(eventRequest)
            .retrieve()
            .bodyToMono(EventResponse.class);
    }
}