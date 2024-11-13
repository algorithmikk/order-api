package com.umameats.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperFieldModel.DynamoDBAttributeType;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTyped;

import lombok.Data;

@Data
@DynamoDBDocument
public class OrderItem {
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String itemId;
    
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String itemName;
    
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Integer quantity;
    
    @DynamoDBTyped(DynamoDBAttributeType.N)
    private Double price;
    
    @DynamoDBTyped(DynamoDBAttributeType.S)
    private String specialInstructions;
}
