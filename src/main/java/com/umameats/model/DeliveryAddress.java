package com.umameats.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;

import lombok.Data;

@Data
@DynamoDBDocument
public class DeliveryAddress {
    private String street;
    private String city;
    private String state;
    private String zipCode;
    private String specialInstructions;
}
