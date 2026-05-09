package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShippingAddress {
    private String street1;
    private String street2;
    private String city;
    private String state;
    private String zip;
    private String phone;
}
