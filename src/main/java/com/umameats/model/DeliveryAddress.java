package com.umameats.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean
public class DeliveryAddress {
    private String fullName;
    private String phone;
    private String street;
    private String apartmentUnit;
    private String buzzCode;
    private String floor;
    private String buildingType;
    private String city;
    private String state;
    @JsonAlias("postalCode")
    private String zipCode;
    private String country;
    private String specialInstructions;
    private Double latitude;
    private Double longitude;
}
