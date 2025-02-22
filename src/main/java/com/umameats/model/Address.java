package com.umameats.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperFieldModel.DynamoDBAttributeType;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTyped;

import lombok.Data;

@Data
@DynamoDBDocument  // Add this annotation
 public class Address {
    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String line1;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String city;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String state;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String postalCode;
}