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
                "https://customer.umameats.com",
                "https://ops-control-center-theta.vercel.app"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            .allowedHeaders("Content-Type", "X-Customer-Id", "X-Admin-Token", "Authorization", "*")
            .exposedHeaders("X-Customer-Id")
            .allowCredentials(true)
            .maxAge(3600);
    }
}