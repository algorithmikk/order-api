package com.umameats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Geocoding Service
 * 
 * Converts text addresses to GPS coordinates (latitude/longitude) using Google Maps Geocoding API.
 * 
 * Features:
 * - Caching to reduce API calls and costs
 * - Error handling with fallback to null coordinates
 * - Supports full address strings or individual components
 * 
 * API Key stored in AWS Secrets Manager: prod/google-maps-api-key
 * 
 * @author UmaEats Engineering
 * @version 1.0
 */
@Slf4j
@Service
public class GeocodingService {
    
    private static final String GEOCODING_API_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    
    @Value("${google.maps.api-key:#{null}}")
    private String apiKey;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public GeocodingService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Geocode a full address string
     * 
     * @param address Full address (e.g., "123 Main St, New York, NY 10001")
     * @return Coordinates or null if geocoding fails
     */
    @Cacheable(value = "geocoding", key = "#address", unless = "#result == null")
    public Coordinates geocode(String address) {
        if (address == null || address.trim().isEmpty()) {
            log.warn("Cannot geocode empty address");
            return null;
        }
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("Google Maps API key not configured - skipping geocoding");
            return null;
        }
        
        try {
            String url = UriComponentsBuilder.fromHttpUrl(GEOCODING_API_URL)
                    .queryParam("address", address)
                    .queryParam("key", apiKey)
                    .toUriString();

            log.info("Geocoding address: {} with URL: {}", address, url);

            // Add custom headers to mimic browser request
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            headers.set("Accept", "application/json");
            headers.set("Accept-Language", "en-US,en;q=0.9");

            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
            org.springframework.http.ResponseEntity<String> responseEntity = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                entity,
                String.class
            );

            String response = responseEntity.getBody();
            log.info("Raw Google Maps API response (HTTP {}): {}", responseEntity.getStatusCode(), response);

            JsonNode root = objectMapper.readTree(response);

            String status = root.path("status").asText();
            log.info("Google Maps API response status: {} for address: {}", status, address);

            if ("OK".equals(status)) {
                JsonNode location = root.path("results").get(0)
                        .path("geometry").path("location");

                double lat = location.path("lat").asDouble();
                double lng = location.path("lng").asDouble();

                log.info("Geocoded address '{}' to ({}, {})", address, lat, lng);
                return new Coordinates(lat, lng);
            } else if ("ZERO_RESULTS".equals(status)) {
                log.warn("No results found for address: {}. Full response: {}", address, response);
                return null;
            } else {
                log.error("Geocoding API error: {} for address: {}. Full response: {}", status, address, response);
                return null;
            }
        } catch (Exception e) {
            log.error("Error geocoding address: {}", address, e);
            return null;
        }
    }
    
    /**
     * Geocode address from components
     *
     * @param street Street address
     * @param city City
     * @param state State/Province
     * @param zipCode ZIP/Postal code
     * @param country Country
     * @return Coordinates or null if geocoding fails
     */
    public Coordinates geocode(String street, String city, String state, String zipCode, String country) {
        StringBuilder addressBuilder = new StringBuilder();

        if (street != null && !street.trim().isEmpty()) {
            addressBuilder.append(street);
        }
        if (city != null && !city.trim().isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(city);
        }
        if (state != null && !state.trim().isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(state);
        }
        if (zipCode != null && !zipCode.trim().isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(" ");
            addressBuilder.append(zipCode);
        }
        if (country != null && !country.trim().isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(country);
        }

        String fullAddress = addressBuilder.toString();

        if (fullAddress.isEmpty()) {
            log.warn("Cannot geocode - all address components are empty");
            return null;
        }

        return geocode(fullAddress);
    }
    
    /**
     * Coordinates data class
     */
    @Data
    public static class Coordinates {
        private final double latitude;
        private final double longitude;
        
        public Coordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}

