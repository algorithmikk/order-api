package com.umameats.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean
public class DeliveryAddress {
    private String fullName;
    private String phone;
    private String street;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String specialInstructions;
    private Double latitude;
    private Double longitude;
}
