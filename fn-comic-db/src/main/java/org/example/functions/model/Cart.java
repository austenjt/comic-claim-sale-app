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
    private Boolean discountExcludesSets; // true when any active discount rule excluded set items
    private List<CartDiscount> discountBreakdown = new ArrayList<>(); // per-rule breakdown for frontend per-item display
    private double shippingCost;         // snapshotted at submit time
    private String paymentStatus;        // UNPAID | PARTIAL | PAID — set by admin
    private Boolean shipped;             // true once admin marks order as shipped
    private String trackingNumber;       // optional shipping tracking number
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

    public Boolean getShipped() { return shipped; }
    public void setShipped(Boolean shipped) { this.shipped = shipped; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getCustomerNotes() { return customerNotes; }
    public void setCustomerNotes(String customerNotes) { this.customerNotes = customerNotes; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

    public Boolean getDiscountExcludesSets() { return discountExcludesSets; }
    public void setDiscountExcludesSets(Boolean discountExcludesSets) { this.discountExcludesSets = discountExcludesSets; }

    public List<CartDiscount> getDiscountBreakdown() { return discountBreakdown; }
    public void setDiscountBreakdown(List<CartDiscount> discountBreakdown) {
        this.discountBreakdown = discountBreakdown != null ? discountBreakdown : new ArrayList<>();
    }
}
