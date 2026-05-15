package com.umameats.controller;

import com.umameats.service.PromoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for promo code validation.
 * Allows frontend to validate and apply discount codes before checkout.
 */
@RestController
@RequestMapping("/api/v1/promo")
@RequiredArgsConstructor
@Slf4j
public class PromoController {

    private final PromoService promoService;

    /**
     * Validate a promo code against a subtotal.
     * Returns discount amount and type if valid.
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validatePromo(@RequestBody Map<String, Object> request) {
        String code = request.get("code") != null ? request.get("code").toString() : null;
        Number subtotal = request.get("subtotal") instanceof Number ? (Number) request.get("subtotal") : null;

        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Promo code is required"));
        }
        if (subtotal == null || subtotal.longValue() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subtotal is required"));
        }

        try {
            var result = promoService.validatePromo(code.trim().toUpperCase(), subtotal.longValue());
            if (result.isValid()) {
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "code", code.trim().toUpperCase(),
                    "discount", result.getDiscount(),
                    "type", result.getType(),
                    "message", "Promo applied successfully"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "code", code.trim().toUpperCase(),
                    "discount", 0,
                    "type", "none",
                    "message", result.getMessage()
                ));
            }
        } catch (Exception e) {
            log.error("Error validating promo code", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to validate promo code"));
        }
    }
}
