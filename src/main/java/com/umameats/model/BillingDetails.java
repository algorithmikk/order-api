package com.umameats.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean
public class BillingDetails {
    private String name;
    private String email;
    private String phone;
    private String currency;
    private Address address;
}
