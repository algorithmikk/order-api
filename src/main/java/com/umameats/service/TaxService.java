package com.umameats.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Tax calculation service for different regions.
 *
 * Tax rates:
 * - Canada (Quebec): GST 5% + QST 9.975% = 14.975%
 * - Canada (Other provinces): GST 5% + PST varies (0-10%)
 * - Belgium: VAT 21% (food delivery)
 * - UAE: VAT 5%
 */
@Service
@Slf4j
public class TaxService {

    // Canada GST (federal)
    private static final double CANADA_GST_RATE = 0.05;

    // Canada Provincial Sales Tax rates
    private static final double QUEBEC_QST_RATE = 0.09975;
    private static final double ONTARIO_HST_RATE = 0.13; // Combined HST
    private static final double BC_PST_RATE = 0.07;
    private static final double ALBERTA_PST_RATE = 0.0; // No PST

    // Belgium VAT
    private static final double BELGIUM_VAT_RATE = 0.21;

    // UAE VAT
    private static final double UAE_VAT_RATE = 0.05;

    /**
     * Calculate tax for an order based on delivery address.
     *
     * @param subtotal Order subtotal in cents
     * @param deliveryFee Delivery fee in cents
     * @param serviceFee Service fee in cents
     * @param country Country code (CA, BE, AE)
     * @param province Province/state code (QC, ON, BC, AB for Canada)
     * @return TaxResult with total tax and breakdown
     */
    public TaxResult calculateTax(long subtotal, long deliveryFee, long serviceFee,
                                   String country, String province) {
        if (country == null) {
            log.warn("No country provided for tax calculation, defaulting to 0 tax");
            return TaxResult.builder()
                .totalTax(0L)
                .taxRate(0.0)
                .breakdown(new HashMap<>())
                .build();
        }

        String countryUpper = country.toUpperCase();

        switch (countryUpper) {
            case "CA":
            case "CANADA":
                return calculateCanadaTax(subtotal, deliveryFee, serviceFee, province);
            case "BE":
            case "BELGIUM":
                return calculateBelgiumTax(subtotal, deliveryFee, serviceFee);
            case "AE":
            case "UAE":
            case "UNITED ARAB EMIRATES":
                return calculateUAETax(subtotal, deliveryFee, serviceFee);
            default:
                log.warn("Unknown country for tax: {}, defaulting to 0 tax", country);
                return TaxResult.builder()
                    .totalTax(0L)
                    .taxRate(0.0)
                    .breakdown(new HashMap<>())
                    .build();
        }
    }

    private TaxResult calculateCanadaTax(long subtotal, long deliveryFee, long serviceFee,
                                          String province) {
        // Taxable amount: subtotal + delivery fee + service fee
        long taxableAmount = subtotal + deliveryFee + serviceFee;
        Map<String, Long> breakdown = new HashMap<>();
        double totalRate;

        String provinceUpper = province != null ? province.toUpperCase() : "";

        switch (provinceUpper) {
            case "QC":
            case "QUEBEC":
                // Quebec: GST 5% + QST 9.975%
                long gst = Math.round(taxableAmount * CANADA_GST_RATE);
                long qst = Math.round(taxableAmount * QUEBEC_QST_RATE);
                breakdown.put("GST", gst);
                breakdown.put("QST", qst);
                totalRate = CANADA_GST_RATE + QUEBEC_QST_RATE;
                break;
            case "ON":
            case "ONTARIO":
                // Ontario: HST 13% (combined)
                long hst = Math.round(taxableAmount * ONTARIO_HST_RATE);
                breakdown.put("HST", hst);
                totalRate = ONTARIO_HST_RATE;
                break;
            case "BC":
            case "BRITISH COLUMBIA":
                // BC: GST 5% + PST 7%
                long bcGst = Math.round(taxableAmount * CANADA_GST_RATE);
                long bcPst = Math.round(taxableAmount * BC_PST_RATE);
                breakdown.put("GST", bcGst);
                breakdown.put("PST", bcPst);
                totalRate = CANADA_GST_RATE + BC_PST_RATE;
                break;
            case "AB":
            case "ALBERTA":
                // Alberta: GST 5% only (no PST)
                long abGst = Math.round(taxableAmount * CANADA_GST_RATE);
                breakdown.put("GST", abGst);
                totalRate = CANADA_GST_RATE;
                break;
            default:
                // Default to GST only for unknown provinces
                long defaultGst = Math.round(taxableAmount * CANADA_GST_RATE);
                breakdown.put("GST", defaultGst);
                totalRate = CANADA_GST_RATE;
                log.info("Unknown Canadian province: {}, applying GST only", province);
        }

        long totalTax = breakdown.values().stream().mapToLong(Long::longValue).sum();

        log.info("Canada tax calculated: province={}, taxableAmount={}, totalTax={}, breakdown={}",
            province, taxableAmount, totalTax, breakdown);

        return TaxResult.builder()
            .totalTax(totalTax)
            .taxRate(totalRate)
            .breakdown(breakdown)
            .build();
    }

    private TaxResult calculateBelgiumTax(long subtotal, long deliveryFee, long serviceFee) {
        long taxableAmount = subtotal + deliveryFee + serviceFee;
        long vat = Math.round(taxableAmount * BELGIUM_VAT_RATE);

        Map<String, Long> breakdown = new HashMap<>();
        breakdown.put("VAT", vat);

        log.info("Belgium tax calculated: taxableAmount={}, VAT={}", taxableAmount, vat);

        return TaxResult.builder()
            .totalTax(vat)
            .taxRate(BELGIUM_VAT_RATE)
            .breakdown(breakdown)
            .build();
    }

    private TaxResult calculateUAETax(long subtotal, long deliveryFee, long serviceFee) {
        long taxableAmount = subtotal + deliveryFee + serviceFee;
        long vat = Math.round(taxableAmount * UAE_VAT_RATE);

        Map<String, Long> breakdown = new HashMap<>();
        breakdown.put("VAT", vat);

        log.info("UAE tax calculated: taxableAmount={}, VAT={}", taxableAmount, vat);

        return TaxResult.builder()
            .totalTax(vat)
            .taxRate(UAE_VAT_RATE)
            .breakdown(breakdown)
            .build();
    }

    /**
     * Tax calculation result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxResult {
        private Long totalTax;      // Total tax in cents
        private Double taxRate;     // Combined tax rate as decimal
        private Map<String, Long> breakdown;  // Tax breakdown by type (GST, QST, VAT, etc.)

        /**
         * Convert breakdown to JSON string for storage.
         */
        public String toBreakdownJson() {
            if (breakdown == null || breakdown.isEmpty()) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Long> entry : breakdown.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
    }
}

