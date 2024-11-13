package com.umameats.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;

import lombok.Data;

@Data
@DynamoDBDocument
public class OrderItem {
    private String itemId;
    private String itemName;
    private Integer quantity;
    private Double price;
    private String specialInstructions;
}
