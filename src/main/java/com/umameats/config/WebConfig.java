package com.umameats.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration  // Add this annotation
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(
                "https://www.umameats.com",
                "https://umameats.com",
                "http://localhost:3000",
                "https://customer.umameats.com"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")  // Added PATCH
            .allowedHeaders("Content-Type", "X-Customer-Id", "Authorization", "*")  // Explicitly list important headers
            .exposedHeaders("X-Customer-Id")  // Add this to expose the custom header
            .allowCredentials(true)
            .maxAge(3600);
    }
}