package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArchivedOrder {
    private String id;
    private String userId;
    private String userName;
    private String userEmail;
    private List<ArchivedOrderItem> items;
    private double discountAmount;
    private String discountDescription;
    private String createdAt;
    private String fulfilledAt;
    private String paymentStatus; // UNPAID | PARTIAL | PAID
    private String customerNotes;
    private String adminNotes;
}
