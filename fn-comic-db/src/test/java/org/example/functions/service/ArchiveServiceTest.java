package org.example.functions.service;

import org.example.functions.model.ArchivedOrder;
import org.example.functions.model.ArchivedOrderItem;
import org.example.functions.model.Cart;
import org.example.functions.model.CartDiscount;
import org.example.functions.model.CartItem;
import org.example.functions.model.enums.CartStatus;
import org.example.functions.model.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ArchiveService#toArchivedOrder} — the pure-functional mapping from
 * a fulfilled {@link Cart} to its persistent {@link ArchivedOrder} representation. No Cosmos
 * dependency, so this exercises field propagation directly.
 *
 * <p>The motivation for these tests is that the discount-exclusion fields
 * ({@code wonViaBid}, {@code isGraded}, {@code discountExcludesSets/Auctions/Graded},
 * {@code discountBreakdown}) all need to survive archiving so that the admin orders UI can
 * still recompute exact per-row discounted prices for fulfilled orders.</p>
 */
class ArchiveServiceTest {

    private static CartItem item(String id, double price, boolean wonViaBid, boolean isGraded, Integer collectionGroup) {
        CartItem ci = new CartItem();
        ci.setComicId(id);
        ci.setComicTitle("Title " + id);
        ci.setComicNumber("#" + id);
        ci.setPrice(price);
        ci.setClaimedAt("2026-04-25T00:00:00Z");
        ci.setCollectionGroup(collectionGroup);
        ci.setWonViaBid(wonViaBid);
        ci.setGraded(isGraded);
        return ci;
    }

    private static Cart sampleCart() {
        Cart c = new Cart();
        c.setId("cart-123");
        c.setUserId("user-1");
        c.setUserName("Alice");
        c.setUserEmail("alice@example.com");
        c.setStatus(CartStatus.FULFILLED);
        c.setCreatedAt("2026-04-20T00:00:00Z");
        c.setFulfilledAt("2026-04-25T00:00:00Z");
        c.setPaymentStatus(PaymentStatus.PAID);
        c.setShipped(true);
        c.setTrackingNumber("1Z9999W99999999999");
        c.setCustomerNotes("Please pack carefully");
        c.setAdminNotes("Repeat customer");
        c.setShippingCost(8.50);
        c.setDiscountAmount(15.00);
        c.setDiscountDescription("10% off ($-15.00) (sets, graded excluded)");
        c.setDiscountExcludesSets(Boolean.TRUE);
        c.setDiscountExcludesAuctions(Boolean.TRUE);
        c.setDiscountExcludesGraded(Boolean.TRUE);
        List<CartDiscount> breakdown = new ArrayList<>();
        breakdown.add(new CartDiscount(15.00, "10% off ($-15.00) (sets, graded excluded)",
            true, true, true));
        c.setDiscountBreakdown(breakdown);

        c.setItems(new ArrayList<>(List.of(
            item("1", 50.00, false, false, null),    // standalone, raw
            item("2", 25.00, true,  false, null),    // bid-won
            item("3", 75.00, false, true,  null))));  // graded
        return c;
    }

    @Test
    void toArchivedOrder_copiesTopLevelCartFields() {
        ArchivedOrder order = ArchiveService.toArchivedOrder(sampleCart());
        assertNotNull(order);
        assertEquals("cart-123", order.getId());
        assertEquals("user-1", order.getUserId());
        assertEquals("Alice", order.getUserName());
        assertEquals("alice@example.com", order.getUserEmail());
        assertEquals("2026-04-20T00:00:00Z", order.getCreatedAt());
        assertEquals("2026-04-25T00:00:00Z", order.getFulfilledAt());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(Boolean.TRUE, order.getShipped());
        assertEquals("1Z9999W99999999999", order.getTrackingNumber());
        assertEquals("Please pack carefully", order.getCustomerNotes());
        assertEquals("Repeat customer", order.getAdminNotes());
        assertEquals(8.50, order.getShippingCost(), 0.001);
        assertEquals(15.00, order.getDiscountAmount(), 0.001);
        assertEquals("10% off ($-15.00) (sets, graded excluded)", order.getDiscountDescription());
    }

    @Test
    void toArchivedOrder_copiesDiscountExclusionFlags() {
        ArchivedOrder order = ArchiveService.toArchivedOrder(sampleCart());
        assertEquals(Boolean.TRUE, order.getDiscountExcludesSets());
        assertEquals(Boolean.TRUE, order.getDiscountExcludesAuctions());
        assertEquals(Boolean.TRUE, order.getDiscountExcludesGraded());
    }

    @Test
    void toArchivedOrder_copiesDiscountBreakdown() {
        ArchivedOrder order = ArchiveService.toArchivedOrder(sampleCart());
        assertNotNull(order.getDiscountBreakdown());
        assertEquals(1, order.getDiscountBreakdown().size());
        CartDiscount entry = order.getDiscountBreakdown().get(0);
        assertEquals(15.00, entry.getAmount(), 0.001);
        assertEquals("10% off ($-15.00) (sets, graded excluded)", entry.getDescription());
        assertTrue(entry.isExcludesSets());
        assertTrue(entry.isExcludesAuctions());
        assertTrue(entry.isExcludesGraded());
    }

    @Test
    void toArchivedOrder_copiesItemLevelWonViaBidAndIsGradedFlags() {
        ArchivedOrder order = ArchiveService.toArchivedOrder(sampleCart());
        assertEquals(3, order.getItems().size());

        ArchivedOrderItem standalone = order.getItems().get(0);
        assertEquals("1", standalone.getComicId());
        assertFalse(standalone.isWonViaBid());
        assertFalse(standalone.isGraded());

        ArchivedOrderItem bidWon = order.getItems().get(1);
        assertEquals("2", bidWon.getComicId());
        assertTrue(bidWon.isWonViaBid(), "wonViaBid must propagate to archive so admin UI can compute auction-exclusion math");
        assertFalse(bidWon.isGraded());

        ArchivedOrderItem graded = order.getItems().get(2);
        assertEquals("3", graded.getComicId());
        assertFalse(graded.isWonViaBid());
        assertTrue(graded.isGraded(), "isGraded must propagate to archive so admin UI can compute graded-exclusion math");
    }

    @Test
    void toArchivedOrder_handlesCartWithNoDiscounts() {
        // A fulfilled cart with no discounts should still archive cleanly with null/zero discount fields.
        Cart c = sampleCart();
        c.setDiscountAmount(0.0);
        c.setDiscountDescription(null);
        c.setDiscountExcludesSets(null);
        c.setDiscountExcludesAuctions(null);
        c.setDiscountExcludesGraded(null);
        c.setDiscountBreakdown(new ArrayList<>());

        ArchivedOrder order = ArchiveService.toArchivedOrder(c);
        assertEquals(0.0, order.getDiscountAmount(), 0.001);
        assertEquals(null, order.getDiscountDescription());
        assertEquals(null, order.getDiscountExcludesSets());
        assertEquals(null, order.getDiscountExcludesAuctions());
        assertEquals(null, order.getDiscountExcludesGraded());
        assertNotNull(order.getDiscountBreakdown());
        assertTrue(order.getDiscountBreakdown().isEmpty());
    }
}
