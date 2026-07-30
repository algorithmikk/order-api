package com.umameats.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Single pooled, timeout-bounded {@link RestTemplate} for all outbound HTTP.
 *
 * <p>A bare {@code new RestTemplate()} has no connection pool and, worse, no
 * timeouts — a slow upstream parks the caller indefinitely. Virtual threads remove
 * the servlet-thread ceiling that used to make that visible as backpressure, so it
 * surfaces as unbounded latency instead. Every limit here is explicit: Apache
 * HttpClient 5 otherwise defaults to 25 total connections and only 5 per route.
 */
@Configuration
public class HttpClientConfig {

    @Value("${http.client.max-connections:100}")
    private int maxConnections;

    @Value("${http.client.max-connections-per-route:25}")
    private int maxConnectionsPerRoute;

    @Value("${http.client.connect-timeout-ms:3000}")
    private long connectTimeoutMs;

    @Value("${http.client.read-timeout-ms:10000}")
    private long readTimeoutMs;

    @Value("${http.client.pool-acquire-timeout-ms:2000}")
    private long poolAcquireTimeoutMs;

    @Bean
    public RestTemplate restTemplate() {
        PoolingHttpClientConnectionManager pool = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(maxConnections)
                .setMaxConnPerRoute(maxConnectionsPerRoute)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                        .setSocketTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                        .build())
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(pool)
                .setDefaultRequestConfig(RequestConfig.custom()
                        // Shed load when the pool is exhausted instead of queueing.
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(poolAcquireTimeoutMs))
                        .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                        .build())
                .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }
}
