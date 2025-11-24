package com.umameats.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache Configuration
 * 
 * Enables caching for geocoding results to reduce API calls and costs.
 * Uses in-memory cache (ConcurrentHashMap) for simplicity.
 * 
 * For production with multiple instances, consider using Redis cache.
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("geocoding");
    }
}

