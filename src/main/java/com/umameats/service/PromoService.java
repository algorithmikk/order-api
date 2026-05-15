package com.umameats.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Service for validating promo codes.
 * MVP implementation uses hardcoded codes; migrate to DynamoDB as promo volume grows.
 */
@Slf4j
@Service
public class PromoService {

    private static final Map<String, PromoDefinition> PROMO_CODES = Map.of(
        "WELCOME15", new PromoDefinition("percent", 15, 0L, 100000L, "First order 15% off"),
        "UMAMEATS20", new PromoDefinition("percent", 20, 0L, 50000L, "20% off orders over $5"),
        "FREE5", new PromoDefinition("fixed", 0, 500L, 2500L, "$5 off your order")
    );

    private static final Set<String> SINGLE_USE_CODES = Set.of("WELCOME15");

    public PromoValidationResult validatePromo(String code, long subtotalCents) {
        PromoDefinition promo = PROMO_CODES.get(code);

        if (promo == null) {
            return PromoValidationResult.invalid("Invalid promo code");
        }

        if (subtotalCents < promo.getMinSubtotalCents()) {
            return PromoValidationResult.invalid(
                String.format("Minimum order of $%.2f required", promo.getMinSubtotalCents() / 100.0));
        }

        if (subtotalCents > promo.getMaxSubtotalCents()) {
            return PromoValidationResult.invalid("Promo not applicable for this order amount");
        }

        long discount;
        if ("percent".equals(promo.getType())) {
            discount = Math.round(subtotalCents * promo.getValue() / 100.0);
            // Cap percent discounts at reasonable amount
            discount = Math.min(discount, 20000L); // $200 cap
        } else {
            discount = promo.getValue();
            discount = Math.min(discount, subtotalCents); // Can't exceed subtotal
        }

        log.info("Promo {} validated: {} {} discount = {} cents on subtotal {}",
            code, promo.getValue(), promo.getType(), discount, subtotalCents);

        return PromoValidationResult.valid(discount, promo.getType());
    }

    @Data
    private static class PromoDefinition {
        private final String type;      // "percent" or "fixed"
        private final int value;        // percent value or fixed cents
        private final long minSubtotalCents;
        private final long maxSubtotalCents;
        private final String description;
    }

    @Data
    public static class PromoValidationResult {
        private final boolean valid;
        private final long discount;
        private final String type;
        private final String message;

        public static PromoValidationResult valid(long discount, String type) {
            return new PromoValidationResult(true, discount, type, "Promo applied successfully");
        }

        public static PromoValidationResult invalid(String message) {
            return new PromoValidationResult(false, 0, "none", message);
        }
    }
}
