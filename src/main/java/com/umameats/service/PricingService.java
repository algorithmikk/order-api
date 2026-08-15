package com.umameats.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralized pricing engine for UMAMEATS.
 * Calculates delivery fees, service fees, platform fees, and validates tips.
 * 
 * All amounts are in CENTS to avoid floating point precision issues.
 */
@Service
@Slf4j
public class PricingService {

    // Delivery fee constants (in cents)
    private static final long DELIVERY_BASE_FEE_CENTS = 299L;  // $2.99
    private static final long DELIVERY_PER_KM_CENTS = 50L;     // $0.50 per km
    private static final double FREE_DISTANCE_KM = 2.0;         // First 2km free
    private static final long DELIVERY_MIN_CENTS = 400L;        // $4.00 minimum (guaranteed driver base payout)
    private static final long DELIVERY_MAX_CENTS = 999L;        // $9.99 maximum

    // Service fee constants (in cents)
    private static final double SERVICE_FEE_PERCENTAGE = 0.05;  // 5% of subtotal
    private static final long SERVICE_FEE_MIN_CENTS = 99L;      // $0.99 minimum
    private static final long SERVICE_FEE_MAX_CENTS = 499L;     // $4.99 maximum

    // Platform fee constants
    private static final double PLATFORM_FEE_PERCENTAGE = 0.15; // 15% of subtotal

    // Tip constants
    private static final long TIP_MAX_CENTS = 50000L;           // $500 maximum tip

    /**
     * Calculate delivery fee based on distance.
     * Formula: $2.99 base + $0.50 per km after first 2km, min $2.99, max $9.99
     *
     * @param distanceKm Distance in kilometers (can be null, defaults to base fee)
     * @return Delivery fee in cents
     */
    public long calculateDeliveryFee(Double distanceKm) {
        if (distanceKm == null || distanceKm <= 0) {
            log.info("Distance not provided, using base delivery fee: {} cents", DELIVERY_BASE_FEE_CENTS);
            return DELIVERY_BASE_FEE_CENTS;
        }

        // Calculate distance-based fee
        double chargeableDistance = Math.max(0, distanceKm - FREE_DISTANCE_KM);
        long distanceFee = Math.round(chargeableDistance * DELIVERY_PER_KM_CENTS);
        long totalFee = DELIVERY_BASE_FEE_CENTS + distanceFee;

        // Apply min/max bounds (also enforces $4.00 guaranteed minimum driver payout)
        long finalFee = Math.max(DELIVERY_MIN_CENTS, Math.min(DELIVERY_MAX_CENTS, totalFee));

        log.info("Calculated delivery fee: distance={}km, chargeableDistance={}km, fee={} cents",
                distanceKm, chargeableDistance, finalFee);

        return finalFee;
    }

    /**
     * Calculate delivery fee based on order subtotal (fallback when distance not available).
     * Formula: 10% of subtotal, min $4.00 (guaranteed driver base), max $9.99
     *
     * @param subtotalCents Order subtotal in cents
     * @return Delivery fee in cents
     */
    public long calculateDeliveryFeeFromSubtotal(Long subtotalCents) {
        if (subtotalCents == null || subtotalCents <= 0) {
            return DELIVERY_MIN_CENTS;
        }

        long fee = Math.round(subtotalCents * 0.10);
        long finalFee = Math.max(DELIVERY_MIN_CENTS, Math.min(DELIVERY_MAX_CENTS, fee));

        log.info("Calculated delivery fee from subtotal: subtotal={} cents, fee={} cents",
                subtotalCents, finalFee);

        return finalFee;
    }

    /**
     * Calculate service fee (platform fee charged to customer).
     * Formula: 5% of subtotal, min $0.99, max $4.99
     *
     * @param subtotalCents Order subtotal in cents
     * @return Service fee in cents
     */
    public long calculateServiceFee(Long subtotalCents) {
        if (subtotalCents == null || subtotalCents <= 0) {
            return SERVICE_FEE_MIN_CENTS;
        }
        long fee = Math.round(subtotalCents * SERVICE_FEE_PERCENTAGE);
        long finalFee = Math.max(SERVICE_FEE_MIN_CENTS, Math.min(SERVICE_FEE_MAX_CENTS, fee));
        log.info("Calculated service fee: subtotal={} cents, fee={} cents", subtotalCents, finalFee);
        return finalFee;
    }

    /**
     * Calculate platform fee (commission taken from restaurant).
     * Formula: 15% of subtotal
     *
     * @param subtotalCents Order subtotal in cents
     * @return Platform fee in cents
     */
    public long calculatePlatformFee(Long subtotalCents) {
        if (subtotalCents == null || subtotalCents <= 0) {
            return 0L;
        }

        long fee = Math.round(subtotalCents * PLATFORM_FEE_PERCENTAGE);
        log.info("Calculated platform fee: subtotal={} cents, fee={} cents ({}%)",
                subtotalCents, fee, PLATFORM_FEE_PERCENTAGE * 100);

        return fee;
    }

    /**
     * Validate and sanitize tip amount.
     * Tips must be non-negative and have a reasonable maximum.
     *
     * @param tipCents Tip amount in cents (can be null)
     * @return Validated tip in cents
     */
    public long validateTip(Long tipCents) {
        if (tipCents == null || tipCents < 0) {
            return 0L;
        }

        if (tipCents > TIP_MAX_CENTS) {
            log.warn("Tip {} cents exceeds maximum, capping at {} cents", tipCents, TIP_MAX_CENTS);
            return TIP_MAX_CENTS;
        }

        return tipCents;
    }

    /**
     * Calculate subtotal from order items.
     * Note: OrderItem.price is stored as Double (cents), we convert to long.
     *
     * @param items List of order items with price in cents
     * @return Subtotal in cents
     */
    public long calculateSubtotal(java.util.List<com.umameats.model.OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return 0L;
        }

        long subtotal = items.stream()
                .mapToLong(item -> {
                    // Price is stored as Double in OrderItem, convert to long
                    long price = item.getPrice() != null ? Math.round(item.getPrice()) : 0L;
                    int quantity = item.getQuantity() != null ? item.getQuantity() : 1;
                    return price * quantity;
                })
                .sum();

        log.info("Calculated subtotal from {} items: {} cents", items.size(), subtotal);
        return subtotal;
    }
}

