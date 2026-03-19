package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Cart {
    private String id;
    private String userId;
    private String userName;
    private String userEmail;
    private List<CartItem> items = new ArrayList<>();
    // OPEN | FINALIZING | FINALIZED | FULFILLED
    private String status;
    private String createdAt;
    private String finalizeAfter;  // ISO-8601, set when user submits order (now + 20h)
    private String finalizedAt;
    private String fulfilledAt;
    private double discountAmount;       // snapshotted at submit time
    private String discountDescription;  // human-readable summary of applied discounts
    private double shippingCost;         // snapshotted at submit time
    private String paymentStatus;        // UNPAID | PARTIAL | PAID — set by admin
    private String customerNotes;        // optional message from user, captured at submit time
    private String adminNotes;           // internal notes from admin

    public Cart() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getFinalizeAfter() { return finalizeAfter; }
    public void setFinalizeAfter(String finalizeAfter) { this.finalizeAfter = finalizeAfter; }

    public String getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(String finalizedAt) { this.finalizedAt = finalizedAt; }

    public String getFulfilledAt() { return fulfilledAt; }
    public void setFulfilledAt(String fulfilledAt) { this.fulfilledAt = fulfilledAt; }

    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }

    public String getDiscountDescription() { return discountDescription; }
    public void setDiscountDescription(String discountDescription) { this.discountDescription = discountDescription; }

    public double getShippingCost() { return shippingCost; }
    public void setShippingCost(double shippingCost) { this.shippingCost = shippingCost; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getCustomerNotes() { return customerNotes; }
    public void setCustomerNotes(String customerNotes) { this.customerNotes = customerNotes; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
}
