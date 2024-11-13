package com.umameats.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

import java.time.ZonedDateTime;

public class LocalDateTimeConverter implements DynamoDBTypeConverter<String, LocalDateTime> {
    private static final DateTimeFormatter LOCAL_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME; // For timestamps without 'Z'
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME; // For timestamps with 'Z'

    @Override
    public String convert(LocalDateTime time) {
        return time != null ? time.format(LOCAL_FORMATTER) : null;
    }

    @Override
    public LocalDateTime unconvert(String value) {
        if (value == null) {
            return null;
        }
        // Handle both cases: with or without 'Z'
        try {
            if (value.endsWith("Z")) {
                return ZonedDateTime.parse(value, ISO_FORMATTER).toLocalDateTime();
            } else {
                return LocalDateTime.parse(value, LOCAL_FORMATTER);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse LocalDateTime: " + value, e);
        }
    }
}
