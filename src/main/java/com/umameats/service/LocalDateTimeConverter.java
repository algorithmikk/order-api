package com.umameats.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

public class LocalDateTimeConverter implements DynamoDBTypeConverter<String, LocalDateTime> {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public String convert(LocalDateTime time) {
        return time != null ? time.format(formatter) : null;
    }

    @Override
    public LocalDateTime unconvert(String value) {
        return value != null ? LocalDateTime.parse(value, formatter) : null;
    }
}