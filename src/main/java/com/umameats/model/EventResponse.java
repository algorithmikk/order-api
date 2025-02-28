package com.umameats.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventResponse {
    /**
     * Unique identifier for the event
     */
    private String eventId;
    
    /**
     * Status of the event processing (e.g., ACCEPTED, REJECTED, PENDING)
     */
    private String status;
    
    /**
     * When the event was processed by the event service
     */
    private LocalDateTime processedAt;
}
