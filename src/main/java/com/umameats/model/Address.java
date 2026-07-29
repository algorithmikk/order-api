package com.umameats.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean
public class Address {
    private String line1;
    private String city;
    private String state;
    private String postalCode;
}
