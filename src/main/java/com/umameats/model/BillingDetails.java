package com.umameats.model;

import lombok.Data;

@Data
 public class BillingDetails {
    private String name;
    private String email;
    private String phone;
    private Address address;
    private String currency;
}

