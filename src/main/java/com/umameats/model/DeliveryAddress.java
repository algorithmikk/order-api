package com.umameats.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperFieldModel.DynamoDBAttributeType;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTyped;

import lombok.Data;

@Data
@DynamoDBDocument
public class DeliveryAddress {
     @DynamoDBTyped(DynamoDBAttributeType.S)
    private String street;

    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String city;

    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String state;

    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String zipCode;

    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String country;

    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String specialInstructions;

    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Double latitude;

    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Double longitude;
}
