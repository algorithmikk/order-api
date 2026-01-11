package com.umameats.controller;

import com.umameats.model.PricingRequest;
import com.umameats.model.PricingResponse;
import com.umameats.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for pricing calculations.
 * Allows frontend to calculate fees before checkout.
 */
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Slf4j
public class PricingController {

    private final PricingService pricingService;

    /**
     * Calculate pricing for an order.
     * Called by frontend before checkout to display fees.
     */
    @PostMapping("/calculate")
    public ResponseEntity<PricingResponse> calculatePricing(@RequestBody PricingRequest request) {
        log.info("Calculating pricing for store: {}, items: {}, distance: {}km, tip: {} cents",
                request.getStoreId(),
                request.getItems() != null ? request.getItems().size() : 0,
                request.getDeliveryDistanceKm(),
                request.getTipCents());

        try {
            // Calculate subtotal from items
            long subtotal = pricingService.calculateSubtotal(request.getItems());

            // Calculate delivery fee (use distance if available, otherwise use subtotal)
            long deliveryFee;
            if (request.getDeliveryDistanceKm() != null && request.getDeliveryDistanceKm() > 0) {
                deliveryFee = pricingService.calculateDeliveryFee(request.getDeliveryDistanceKm());
            } else {
                deliveryFee = pricingService.calculateDeliveryFeeFromSubtotal(subtotal);
            }

            // Calculate service fee
            long serviceFee = pricingService.calculateServiceFee(subtotal);

            // Validate tip
            long tip = pricingService.validateTip(request.getTipCents());

            // Calculate platform fee (for info)
            long platformFee = pricingService.calculatePlatformFee(subtotal);

            // Calculate total
            long totalAmount = subtotal + deliveryFee + serviceFee + tip;

            // Calculate payouts
            long restaurantPayout = subtotal - platformFee;
            long driverPayout = deliveryFee + tip;

            PricingResponse response = PricingResponse.builder()
                    .subtotal(subtotal)
                    .deliveryFee(deliveryFee)
                    .serviceFee(serviceFee)
                    .tip(tip)
                    .totalAmount(totalAmount)
                    .platformFee(platformFee)
                    .restaurantPayout(restaurantPayout)
                    .driverPayout(driverPayout)
                    .build();

            log.info("Pricing calculated: subtotal={}, deliveryFee={}, serviceFee={}, tip={}, total={}",
                    subtotal, deliveryFee, serviceFee, tip, totalAmount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error calculating pricing", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get suggested tip amounts based on subtotal.
     */
    @GetMapping("/tips")
    public ResponseEntity<Map<String, Long>> getSuggestedTips(@RequestParam Long subtotalCents) {
        if (subtotalCents == null || subtotalCents <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, Long> tips = Map.of(
                "15%", Math.round(subtotalCents * 0.15),
                "18%", Math.round(subtotalCents * 0.18),
                "20%", Math.round(subtotalCents * 0.20),
                "25%", Math.round(subtotalCents * 0.25)
        );

        return ResponseEntity.ok(tips);
    }
}

