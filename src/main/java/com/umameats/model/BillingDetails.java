package com.umameats.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperFieldModel.DynamoDBAttributeType;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTyped;

import lombok.Data;

@Data
@DynamoDBDocument  // Add this annotation
public class BillingDetails {
    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String name;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String email;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String phone;

    @DynamoDBAttribute
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String currency;

    @DynamoDBAttribute
    private Address address;
}


