package org.example.functions.service;

import org.example.functions.model.Cart;
import org.example.functions.model.CartItem;
import org.example.functions.model.Discount;
import org.example.functions.model.enums.DiscountType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DiscountService#computeDiscounts} — the pure-functional
 * discount math, exercised without any Cosmos dependency. Covers the three discount
 * types, each exclude flag in isolation, and combinations of excludes.
 */
class DiscountServiceTest {

    // ─── Test data builders ───────────────────────────────────────────────────

    private static CartItem book(String id, double price) {
        return book(id, price, false, null);
    }

    private static CartItem book(String id, double price, boolean isGraded, Integer collectionGroup) {
        CartItem ci = new CartItem();
        ci.setComicId(id);
        ci.setComicTitle("Title " + id);
        ci.setPrice(price);
        ci.setGraded(isGraded);
        ci.setCollectionGroup(collectionGroup);
        return ci;
    }

    private static Cart cartOf(CartItem... items) {
        Cart c = new Cart();
        c.setId("test-cart");
        c.setItems(new ArrayList<>(Arrays.asList(items)));
        return c;
    }

    private static Discount rawPercent(double percent, boolean excludeSets, boolean excludeGraded) {
        return Discount.builder()
            .id("d-raw")
            .name("Raw")
            .type(DiscountType.RAW_PERCENTAGE)
            .isActive(true)
            .percentageOff(percent)
            .excludeSets(excludeSets)
            .excludeGraded(excludeGraded)
            .build();
    }

    private static Discount buyXGetOneFree(int x, boolean excludeSets, boolean excludeGraded) {
        // Include xBooks in the id so a test using two BUY_X rules with different X gets distinct ids.
        // Tests that need two rules with the SAME X should call buyXGetOneFreeWithId() instead.
        return buyXGetOneFreeWithId("d-bxgo-" + x, x, excludeSets, excludeGraded);
    }

    private static Discount buyXGetOneFreeWithId(String id, int x, boolean excludeSets,
                                                 boolean excludeGraded) {
        return Discount.builder()
            .id(id)
            .name("BXGO-" + id)
            .type(DiscountType.BUY_X_GET_ONE_FREE)
            .isActive(true)
            .xBooks(x)
            .excludeSets(excludeSets)
            .excludeGraded(excludeGraded)
            .build();
    }

    private static Discount percentOver(double percent, int x, boolean excludeSets, boolean excludeGraded) {
        return Discount.builder()
            .id("d-over")
            .name("Over")
            .type(DiscountType.PERCENT_OFF_OVER_X_BOOKS)
            .isActive(true)
            .percentageOff(percent)
            .xBooks(x)
            .excludeSets(excludeSets)
            .excludeGraded(excludeGraded)
            .build();
    }

    // ─── Empty / no-op cases ──────────────────────────────────────────────────

    @Test
    void noActiveDiscounts_returnsZero() {
        var result = DiscountService.computeDiscounts(cartOf(book("1", 10.0)), List.of());
        assertEquals(0.0, result.getAmount());
        assertNull(result.getDescription());
        assertFalse(result.isExcludedSets());
        assertFalse(result.isExcludedGraded());
        assertTrue(result.getBreakdown().isEmpty());
    }

    @Test
    void onlyAwardedItems_yieldZero() {
        CartItem awarded = book("a", 50.0);
        awarded.setAwarded(true);
        var result = DiscountService.computeDiscounts(cartOf(awarded), List.of(rawPercent(50, false, false)));
        assertEquals(0.0, result.getAmount());
    }

    // ─── Baseline RAW_PERCENTAGE without exclusions ──────────────────────────

    @Test
    void rawPercent_appliesToAllItems() {
        var cart = cartOf(book("1", 10.0), book("2", 20.0));
        var result = DiscountService.computeDiscounts(cart, List.of(rawPercent(10, false, false)));
        assertEquals(3.0, result.getAmount(), 0.001);
        assertNotNull(result.getDescription());
        assertEquals(1, result.getBreakdown().size());
    }

    // ─── excludeSets ──────────────────────────────────────────────────────────

    @Test
    void excludeSets_skipsSetMembersFromDiscount() {
        var cart = cartOf(
            book("1", 10.0, false, null),     // standalone
            book("2", 20.0, false, 7));        // set member
        var result = DiscountService.computeDiscounts(cart, List.of(rawPercent(50, true, false)));
        assertEquals(5.0, result.getAmount(), 0.001); // 50% of $10 only
        assertTrue(result.isExcludedSets());
    }

    // ─── excludeGraded ────────────────────────────────────────────────────────

    @Test
    void excludeGraded_skipsGradedItemsFromBothCountAndDiscount() {
        var cart = cartOf(
            book("raw", 10.0, false, null),
            book("graded", 100.0, true, null));
        var result = DiscountService.computeDiscounts(cart, List.of(rawPercent(50, false, true)));
        assertEquals(5.0, result.getAmount(), 0.001); // 50% of $10 only
        assertTrue(result.isExcludedGraded());
    }

    @Test
    void excludeGraded_thresholdRespectsExclusion() {
        // 5 graded + 1 raw. PERCENT_OFF_OVER_X_BOOKS with x=3 should NOT trigger when
        // graded are excluded (only 1 raw item, not > 3).
        var items = new CartItem[6];
        for (int i = 0; i < 5; i++) items[i] = book("g" + i, 10.0, true, null);
        items[5] = book("raw", 10.0);
        var cart = cartOf(items);
        var rule = percentOver(50, 3, false, true);
        var result = DiscountService.computeDiscounts(cart, List.of(rule));
        assertEquals(0.0, result.getAmount(), 0.001);
    }

    // ─── Combinations ─────────────────────────────────────────────────────────

    @Test
    void multipleRulesStack_independentlyApplyTheirExclusions() {
        var cart = cartOf(
            book("raw", 10.0),
            book("graded", 100.0, true, null));
        // Rule A: 10% off everything → $11 savings (10 + 100 → 10% = 11)
        // Rule B: 50% off non-graded   → $5 savings (10 → 50% = 5)
        var result = DiscountService.computeDiscounts(cart, List.of(
            rawPercent(10, false, false),
            rawPercent(50, false, true)));
        assertEquals(16.0, result.getAmount(), 0.001);
        // Combined description should mention both savings
        assertTrue(result.getDescription().contains("10%"));
        assertTrue(result.getDescription().contains("50%"));
    }

    @Test
    void totalSavingsCappedAtBaseSubtotal() {
        // One rule shouldn't be able to drive cart price below zero.
        var cart = cartOf(book("1", 10.0));
        var result = DiscountService.computeDiscounts(cart, List.of(
            rawPercent(80, false, false),
            rawPercent(80, false, false))); // 160% combined
        assertEquals(10.0, result.getAmount(), 0.001); // capped at $10
    }

    // ─── BUY_X_GET_ONE_FREE math ─────────────────────────────────────────────

    @Test
    void buyXGetOneFree_belowThreshold_yieldsZero() {
        // X=3 means we need 4 items total before the cheapest goes free.
        var cart = cartOf(book("1", 10.0), book("2", 10.0), book("3", 10.0));
        var result = DiscountService.computeDiscounts(cart, List.of(buyXGetOneFree(3, false, false)));
        assertEquals(0.0, result.getAmount(), 0.001);
        assertNull(result.getDescription());
    }

    @Test
    void buyXGetOneFree_exactlyOneFree_picksTheCheapest() {
        // X=2 means every 3rd item is free. With prices [5, 10, 20] the $5 book should be free.
        var cart = cartOf(book("a", 20.0), book("b", 10.0), book("c", 5.0));
        var result = DiscountService.computeDiscounts(cart, List.of(buyXGetOneFree(2, false, false)));
        assertEquals(5.0, result.getAmount(), 0.001);
        assertEquals(1, result.getBreakdown().size());
        assertTrue(result.getDescription().contains("Buy 2 get 1 free"));
    }

    @Test
    void buyXGetOneFree_twoFreeBrackets_picksTwoCheapest() {
        // X=2, 6 items → freeCount = 6 / (2+1) = 2. The two cheapest ($1 + $2) go free.
        var cart = cartOf(
            book("a", 100.0),
            book("b", 50.0),
            book("c", 10.0),
            book("d", 5.0),
            book("e", 2.0),
            book("f", 1.0));
        var result = DiscountService.computeDiscounts(cart, List.of(buyXGetOneFree(2, false, false)));
        assertEquals(3.0, result.getAmount(), 0.001); // $1 + $2 = $3
    }

    // ─── PERCENT_OFF_OVER_X_BOOKS math ───────────────────────────────────────

    @Test
    void percentOverX_atExactlyXBooks_yieldsZero() {
        // The rule fires only when itemCount > xBooks (strict).
        var cart = cartOf(book("1", 10.0), book("2", 10.0), book("3", 10.0));
        var result = DiscountService.computeDiscounts(cart, List.of(percentOver(20, 3, false, false)));
        assertEquals(0.0, result.getAmount(), 0.001);
    }

    @Test
    void percentOverX_aboveThreshold_appliesPercentToFullDiscountableSubtotal() {
        // xBooks=3, 4 items at $10 each = $40 subtotal → 25% off = $10.
        var cart = cartOf(book("1", 10.0), book("2", 10.0), book("3", 10.0), book("4", 10.0));
        var result = DiscountService.computeDiscounts(cart, List.of(percentOver(25, 3, false, false)));
        assertEquals(10.0, result.getAmount(), 0.001);
    }

    // ─── Item eligibility edge cases ─────────────────────────────────────────

    @Test
    void setContainerItems_excludedFromCountAndDiscount() {
        // Set container is a $0 item flagged isSetContainer=true. The price-filter alone
        // already drops it; this test makes the intent explicit.
        var container = book("set", 0.0);
        container.setSetContainer(true);
        container.setCollectionGroup(7);
        var member = book("m", 20.0, false, 7);
        var standalone = book("s", 30.0);
        var result = DiscountService.computeDiscounts(cartOf(container, member, standalone),
            List.of(rawPercent(10, false, false)));
        // Discount applies to $20 + $30 = $50 → 10% = $5. Container does not contribute.
        assertEquals(5.0, result.getAmount(), 0.001);
    }

    @Test
    void zeroPricedItems_excludedFromCountAndDiscount() {
        // A $0 non-container item still gets filtered out of baseItems by the price > 0 guard.
        var cart = cartOf(book("free", 0.0), book("paid", 50.0));
        var result = DiscountService.computeDiscounts(cart, List.of(buyXGetOneFree(1, false, false)));
        // X=1 wants 2 items; only one passes the filter, so no free.
        assertEquals(0.0, result.getAmount(), 0.001);
    }

    @Test
    void awardedItemsAreSkippedEvenWhenPriced() {
        // isAwarded=true should drop the item even if its price is non-zero.
        CartItem awarded = book("award", 100.0);
        awarded.setAwarded(true);
        var cart = cartOf(awarded, book("paid", 10.0));
        var result = DiscountService.computeDiscounts(cart, List.of(rawPercent(50, false, false)));
        assertEquals(5.0, result.getAmount(), 0.001); // 50% of $10, awarded $100 ignored
    }

    // ─── Description string format ───────────────────────────────────────────

    @Test
    void descriptionFormat_eachDiscountType() {
        // RAW_PERCENTAGE: "50% off ($-X.XX)"
        var rawCart = cartOf(book("a", 20.0));
        var rawResult = DiscountService.computeDiscounts(rawCart, List.of(rawPercent(50, false, false)));
        assertEquals("50% off ($-10.00)", rawResult.getDescription());

        // BUY_X_GET_ONE_FREE: "Buy X get 1 free (N free, $-X.XX)"
        var bxgoCart = cartOf(book("a", 5.0), book("b", 10.0), book("c", 15.0));
        var bxgoResult = DiscountService.computeDiscounts(bxgoCart, List.of(buyXGetOneFree(2, false, false)));
        assertEquals("Buy 2 get 1 free (1 free, $-5.00)", bxgoResult.getDescription());

        // PERCENT_OFF_OVER_X_BOOKS: "X% off (over Y books, $-Z.ZZ)"
        var overCart = cartOf(book("a", 10.0), book("b", 10.0), book("c", 10.0), book("d", 10.0));
        var overResult = DiscountService.computeDiscounts(overCart, List.of(percentOver(10, 3, false, false)));
        assertEquals("10% off (over 3 books, $-4.00)", overResult.getDescription());
    }

    @Test
    void descriptionFormat_multipleRulesJoinedWithSemicolon() {
        var cart = cartOf(book("a", 100.0), book("b", 100.0), book("c", 100.0), book("d", 100.0));
        var result = DiscountService.computeDiscounts(cart, List.of(
            rawPercent(10, false, false),
            percentOver(5, 3, false, false)));
        // Two rules separated by "; "
        assertTrue(result.getDescription().contains("; "),
            "Expected semicolon-joined description, got: " + result.getDescription());
        assertTrue(result.getDescription().startsWith("10% off"));
        assertTrue(result.getDescription().contains("5% off (over 3 books"));
    }

    @Test
    void descriptionFormat_rulesYieldingZeroSavingsAreOmitted() {
        // BUY_X_GET_ONE_FREE with too few items yields $0 → must NOT appear in description.
        var cart = cartOf(book("a", 10.0), book("b", 10.0)); // X=3 wants 4 items
        var result = DiscountService.computeDiscounts(cart, List.of(
            rawPercent(10, false, false),       // applies → $2
            buyXGetOneFree(3, false, false)));  // does not apply
        assertEquals(2.0, result.getAmount(), 0.001);
        assertEquals("10% off ($-2.00)", result.getDescription());
        // Breakdown also reflects only the rule that applied.
        assertEquals(1, result.getBreakdown().size());
    }

    // ─── CartDiscount breakdown integrity ─────────────────────────────────────

    @Test
    void breakdown_oneEntryPerApplyingRule() {
        // Three rules; the BUY_X_GET_ONE_FREE one yields $0 with too few items.
        var cart = cartOf(book("a", 100.0), book("b", 100.0), book("c", 100.0), book("d", 100.0));
        var result = DiscountService.computeDiscounts(cart, List.of(
            rawPercent(10, false, false),       // applies
            buyXGetOneFree(10, false, false),   // does not apply (need 11 items)
            percentOver(5, 3, false, false))); // applies
        assertEquals(2, result.getBreakdown().size(), "Only the two applying rules should be in the breakdown");
        // Sum of breakdown amounts should equal total savings.
        double sum = result.getBreakdown().stream().mapToDouble(b -> b.getAmount()).sum();
        assertEquals(result.getAmount(), sum, 0.001);
    }

    @Test
    void breakdown_carriesExcludeFlagsFromSourceRule() {
        // Rule with sets+graded excluded.
        var cart = cartOf(book("a", 100.0));
        var result = DiscountService.computeDiscounts(cart, List.of(rawPercent(10, true, true)));
        assertEquals(1, result.getBreakdown().size());
        var entry = result.getBreakdown().get(0);
        assertTrue(entry.isExcludesSets());
        assertTrue(entry.isExcludesGraded());
    }

    // ─── Multi-rule stacking ─────────────────────────────────────────────────

    @Test
    void twoBuyXGetOneFreeRules_dedupePicksAcrossRules() {
        // Regression for the bug a user reported: two BUY_X_GET_ONE_FREE rules in one cart
        // both picked the SAME cheapest book, so the per-row math zeroed out one $0.50 book
        // for both rules and silently lost the second rule's promised savings.
        //
        // Cart shape (mirrors the user's reported scenario, scaled down):
        //   - 22 eligible items: two $0.50 books, one $0.75 book, nineteen $1.00 books
        //   - Rule A: "Buy 10 get 1 free" → freeCount = 22 / 11 = 2
        //   - Rule B: "Buy 20 get 1 free" → freeCount = 22 / 21 = 1
        //
        // Expected after the fix:
        //   - Rule A picks the two $0.50 books → savings $1.00
        //   - Rule B picks the next-cheapest $0.75 book (not the same $0.50!) → savings $0.75
        //   - Total: $1.75 (NOT $1.50, which was the buggy answer)
        List<CartItem> items = new java.util.ArrayList<>();
        items.add(book("a", 0.50));
        items.add(book("b", 0.50));
        items.add(book("c", 0.75));
        for (int i = 0; i < 19; i++) items.add(book("d" + i, 1.00));
        var cart = cartOf(items.toArray(new CartItem[0]));

        var result = DiscountService.computeDiscounts(cart, List.of(
            buyXGetOneFree(10, false, false),
            buyXGetOneFree(20, false, false)));

        assertEquals(1.75, result.getAmount(), 0.001,
            "Two BUY_X rules must free three DISTINCT cheapest items, not free the same book twice");
        assertEquals(2, result.getBreakdown().size());

        // Buy 10 should claim the two $0.50 books (savings $1.00).
        var buy10 = result.getBreakdown().stream()
            .filter(b -> b.getDescription().startsWith("Buy 10"))
            .findFirst().orElseThrow();
        assertEquals(1.00, buy10.getAmount(), 0.001);
        assertTrue(buy10.getDescription().contains("(2 free, $-1.00)"),
            "Buy 10's description should show 2 free books at $1.00 — got: " + buy10.getDescription());

        // Buy 20 should claim the $0.75 book (savings $0.75, NOT $0.50).
        var buy20 = result.getBreakdown().stream()
            .filter(b -> b.getDescription().startsWith("Buy 20"))
            .findFirst().orElseThrow();
        assertEquals(0.75, buy20.getAmount(), 0.001,
            "Buy 20 must pick the $0.75 book — picking another $0.50 would double-count savings");
        assertTrue(buy20.getDescription().contains("(1 free, $-0.75)"),
            "Buy 20's description should reflect the $0.75 pick — got: " + buy20.getDescription());
    }

    @Test
    void twoBuyXGetOneFreeRules_smallerXAlwaysPicksFirst_regardlessOfRuleOrder() {
        // Confirms that the deterministic xBooks-ascending sort means rule order in the
        // input list doesn't change which rule claims which books. Same scenario as above
        // but with the rules passed in reversed order.
        List<CartItem> items = new java.util.ArrayList<>();
        items.add(book("a", 0.50));
        items.add(book("b", 0.50));
        items.add(book("c", 0.75));
        for (int i = 0; i < 19; i++) items.add(book("d" + i, 1.00));
        var cart = cartOf(items.toArray(new CartItem[0]));

        var result = DiscountService.computeDiscounts(cart, List.of(
            buyXGetOneFree(20, false, false),  // larger X passed FIRST
            buyXGetOneFree(10, false, false)));

        // Total must still be $1.75 — sort is by xBooks, not by input order.
        assertEquals(1.75, result.getAmount(), 0.001);
        var buy10 = result.getBreakdown().stream()
            .filter(b -> b.getDescription().startsWith("Buy 10"))
            .findFirst().orElseThrow();
        var buy20 = result.getBreakdown().stream()
            .filter(b -> b.getDescription().startsWith("Buy 20"))
            .findFirst().orElseThrow();
        assertEquals(1.00, buy10.getAmount(), 0.001, "Buy 10 should still claim the cheapest two");
        assertEquals(0.75, buy20.getAmount(), 0.001, "Buy 20 should still pick the leftover $0.75");
    }

    @Test
    void twoPercentOverRules_stackIndependently_neitherAffectsTheOther() {
        // 20 books at $10 each = $200 subtotal. Two "1% off over 10 books" rules each
        // see all 20 items in their own count and apply 1% to the full subtotal.
        // Expected total savings = 1% + 1% = 2% of $200 = $4.
        CartItem[] items = new CartItem[20];
        for (int i = 0; i < 20; i++) items[i] = book("b" + i, 10.0);
        var cart = cartOf(items);
        var result = DiscountService.computeDiscounts(cart, List.of(
            percentOver(1, 10, false, false),
            percentOver(1, 10, false, false)));
        assertEquals(4.0, result.getAmount(), 0.001);
        assertEquals(2, result.getBreakdown().size());
        // Each rule should see itemCount=20 and discount the full $200.
        for (var entry : result.getBreakdown()) {
            assertEquals(2.0, entry.getAmount(), 0.001);
        }
    }
}
