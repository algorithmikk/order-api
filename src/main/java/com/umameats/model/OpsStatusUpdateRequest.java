package com.umameats.model;

import lombok.Data;

/**
 * Ops-only force status update body.
 */
@Data
public class OpsStatusUpdateRequest {
    private String status;
    private String deliveryStatus;
    private Boolean clearDriver;
    private Double restaurantLat;
    private Double restaurantLng;
    private String note;
}
