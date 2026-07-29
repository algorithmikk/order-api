package com.umameats.service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class LocalDateTimeAttributeConverter implements AttributeConverter<LocalDateTime> {
    private static final DateTimeFormatter LOCAL_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    @Override
    public AttributeValue transformFrom(LocalDateTime input) {
        return input == null
                ? null
                : AttributeValue.builder().s(input.format(LOCAL_FORMATTER)).build();
    }

    @Override
    public LocalDateTime transformTo(AttributeValue input) {
        if (input == null || input.s() == null) {
            return null;
        }
        String value = input.s();
        try {
            if (value.endsWith("Z")) {
                return ZonedDateTime.parse(value, ISO_FORMATTER).toLocalDateTime();
            }
            return LocalDateTime.parse(value, LOCAL_FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse LocalDateTime: " + value, e);
        }
    }

    @Override
    public EnhancedType<LocalDateTime> type() {
        return EnhancedType.of(LocalDateTime.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
