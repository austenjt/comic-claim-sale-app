package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {
    private String id;
    private String name;
    private String email;
    private String entraOid;  // Entra External ID object ID (oid claim) — stable unique identifier
    private String address;
    private String phone;
    private String notes;
    private String preferences;
    private String venmoHandle;
    private String paypalHandle;
    private String ebayUsername;
    private String cashAppHandle;
    private String status; // "PENDING" | "APPROVED" | "SUSPENDED"
    private String createdDate;
    private String approvedDate;
    @Getter(onMethod_ = {@JsonProperty("isAdmin")})
    @JsonProperty("isAdmin")
    private boolean isAdmin;
}
