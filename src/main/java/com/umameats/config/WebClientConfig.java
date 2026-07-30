package com.umameats.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * The shared {@link org.springframework.web.client.RestTemplate} lives in
 * {@link HttpClientConfig}, which bounds its pool and timeouts.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}