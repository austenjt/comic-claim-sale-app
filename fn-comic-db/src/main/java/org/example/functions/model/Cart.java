package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.functions.model.enums.CartStatus;
import org.example.functions.model.enums.PaymentStatus;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Cart {
    private String id;
    private String userId;
    private String userName;
    private String userEmail;
    private List<CartItem> items = new ArrayList<>();
    private CartStatus status;
    private String createdAt;
    private String finalizeAfter;  // ISO-8601, set when user submits order (now + 20h)
    private String finalizedAt;
    private String fulfilledAt;
    private double discountAmount;       // snapshotted at submit time
    private String discountDescription;  // human-readable summary of applied discounts
    private Boolean discountExcludesSets; // true when any active discount rule excluded set items
    private Boolean discountExcludesGraded; // true when any active discount rule excluded graded comics
    private List<CartDiscount> discountBreakdown = new ArrayList<>(); // per-rule breakdown for frontend per-item display
    private double shippingCost;          // snapshotted at submit time
    private boolean freeShippingApplied; // true when a FREE_SHIPPING_OVER_X_BOOKS rule waived shipping
    private PaymentStatus paymentStatus; // set by admin
    private Boolean shipped;             // true once admin marks order as shipped
    private String trackingNumber;       // optional shipping tracking number
    private String customerNotes;        // optional message from user, captured at submit time
    private String adminNotes;           // internal notes from admin
    private String invoiceNumber;        // human-readable order number, e.g. "LCR-0042", set on submission
    private ShippingAddress shippingAddress; // collected during checkout after order submission
    /**
     * Set to {@code true} by admin once the physical trade-in item has been received.
     * Fulfillment is blocked on carts with trade items until this is true.
     * Null/false for carts that have no trade items (legacy and normal orders).
     */
    private Boolean tradeReceived;

    /**
     * Cosmos {@code _etag} captured at read time, used for optimistic-concurrency
     * protected writes. Marked {@link JsonIgnore} so it never appears in API responses or
     * persisted documents — Cosmos manages the real {@code _etag} system property itself.
     */
    @JsonIgnore
    private String etag;

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

    public CartStatus getStatus() { return status; }
    public void setStatus(CartStatus status) { this.status = status; }

    /** Convenience for {@code status == target}. Null-safe. */
    public boolean is(CartStatus target) { return this.status == target; }

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

    public boolean isFreeShippingApplied() { return freeShippingApplied; }
    public void setFreeShippingApplied(boolean freeShippingApplied) { this.freeShippingApplied = freeShippingApplied; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public Boolean getShipped() { return shipped; }
    public void setShipped(Boolean shipped) { this.shipped = shipped; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getCustomerNotes() { return customerNotes; }
    public void setCustomerNotes(String customerNotes) { this.customerNotes = customerNotes; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(ShippingAddress shippingAddress) { this.shippingAddress = shippingAddress; }

    public Boolean getDiscountExcludesSets() { return discountExcludesSets; }
    public void setDiscountExcludesSets(Boolean discountExcludesSets) { this.discountExcludesSets = discountExcludesSets; }

    public Boolean getDiscountExcludesGraded() { return discountExcludesGraded; }
    public void setDiscountExcludesGraded(Boolean discountExcludesGraded) { this.discountExcludesGraded = discountExcludesGraded; }

    public List<CartDiscount> getDiscountBreakdown() { return discountBreakdown; }
    public void setDiscountBreakdown(List<CartDiscount> discountBreakdown) {
        this.discountBreakdown = discountBreakdown != null ? discountBreakdown : new ArrayList<>();
    }

    public Boolean getTradeReceived() { return tradeReceived; }
    public void setTradeReceived(Boolean tradeReceived) { this.tradeReceived = tradeReceived; }

    /** Convenience: true if this cart contains at least one trade-in item. */
    public boolean hasTradeItem() {
        return items != null && items.stream().anyMatch(CartItem::isTrade);
    }

    /** Cosmos {@code _etag} captured at read time. Excluded from JSON serialization. */
    @JsonIgnore
    public String getEtag() { return etag; }
    @JsonIgnore
    public void setEtag(String etag) { this.etag = etag; }
}
