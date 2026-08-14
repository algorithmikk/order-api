package com.umameats.model;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Delivery PIN is always a 4-digit string. Older rows may have stored it as a
 * DynamoDB Number, which the default String mapper silently drops — the customer
 * GET then mints a second PIN and POD fails.
 */
public class DeliveryPinAttributeConverter implements AttributeConverter<String> {

    @Override
    public AttributeValue transformFrom(String input) {
        String normalized = canonicalize(input);
        if (normalized.isEmpty()) {
            return AttributeValue.fromNul(true);
        }
        return AttributeValue.fromS(normalized);
    }

    @Override
    public String transformTo(AttributeValue input) {
        if (input == null || Boolean.TRUE.equals(input.nul())) {
            return null;
        }
        if (input.s() != null) {
            return canonicalize(input.s());
        }
        if (input.n() != null) {
            return canonicalize(input.n());
        }
        return null;
    }

    @Override
    public EnhancedType<String> type() {
        return EnhancedType.of(String.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }

    public static String canonicalize(String pin) {
        if (pin == null) {
            return "";
        }
        String digits = pin.trim().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return "";
        }
        if (digits.length() >= 4) {
            return digits;
        }
        return String.format("%04d", Integer.parseInt(digits));
    }

    public static String fromAttribute(AttributeValue value) {
        if (value == null) {
            return "";
        }
        DeliveryPinAttributeConverter converter = new DeliveryPinAttributeConverter();
        String pin = converter.transformTo(value);
        return pin != null ? pin : "";
    }
}
