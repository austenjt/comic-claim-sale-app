package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.functions.model.enums.PaymentStatus;

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
    /** Snapshotted from {@code Cart.discountExcludesSets} at fulfillment time. */
    private Boolean discountExcludesSets;
    /** Snapshotted from {@code Cart.discountExcludesGraded} at fulfillment time. */
    private Boolean discountExcludesGraded;
    /** Snapshotted from {@code Cart.discountBreakdown} so per-rule math survives archive. */
    private List<CartDiscount> discountBreakdown;
    private double shippingCost;
    private String createdAt;
    private String fulfilledAt;
    private PaymentStatus paymentStatus;
    private Boolean shipped;
    private String trackingNumber;
    private String customerNotes;
    private String adminNotes;
    private String invoiceNumber;
    private ShippingAddress shippingAddress;
}
