import { Component, OnInit, OnDestroy } from '@angular/core';
import { CartService } from '../cart.service';
import { AuthService } from '../auth.service';
import { Cart, CartDiscount, CartItem } from '../cart';

interface CartRow {
  type: 'single' | 'set';
  collectionGroup: number | null;
  items: CartItem[];
  totalPrice: number;
  claimedAt: string;
  removeId: string;
  /** True for single rows when the item is graded; for set rows, true if ANY member is graded. */
  isGraded: boolean;
  containerTitle?: string;
  containerId?: string;
}

@Component({
    selector: 'app-cart',
    templateUrl: './cart.component.html',
    styleUrls: ['./cart.component.css'],
    standalone: false
})
export class CartComponent implements OnInit, OnDestroy {
  cart: Cart | null = null;
  loading = false;
  error = '';
  submitting = false;
  customerNotes = '';
  showPaymentModal = false;
  showShippingModal = false;
  paymentSuccess = false;
  payLaterAcknowledged = false;
  confirmModal: {
    comicId: string;
    setTitle: string;
    bookCount: number;
    isFinalizing: boolean;
  } | null = null;

  constructor(
    private cartService: CartService,
    public auth: AuthService
  ) {}

  ngOnInit() {
    this.loadCart();
  }

  ngOnDestroy() {}

  loadCart() {
    this.loading = true;
    this.cartService.getMyCart().subscribe({
      next: cart => { this.cart = cart; this.loading = false; },
      error: () => { this.error = 'Failed to load cart.'; this.loading = false; }
    });
  }

  removeItem(comicId: string) {
    const item = this.cart?.items.find(i => i.comicId === comicId);
    const isSetItem = item?.collectionGroup != null && item.collectionGroup > 0;
    const setCount = isSetItem
      ? (this.cart?.items.filter(i => i.collectionGroup === item!.collectionGroup && !i.isSetContainer).length ?? 0)
      : 0;

    if (isSetItem) {
      // Find the container title for the modal heading
      const container = this.cart?.items.find(i => i.isSetContainer && i.collectionGroup === item!.collectionGroup);
      this.confirmModal = {
        comicId,
        setTitle: container?.comicTitle ?? item?.comicTitle ?? 'this set',
        bookCount: setCount,
        isFinalizing: this.cart?.status === 'FINALIZING'
      };
      return;
    }

    if (this.cart?.status === 'FINALIZING') {
      if (!window.confirm('Are you sure you want to remove a book from an established order?')) return;
    }

    this.executeRelease(comicId, item, false, 0);
  }

  confirmRelease() {
    if (!this.confirmModal) return;
    const { comicId } = this.confirmModal;
    const item = this.cart?.items.find(i => i.comicId === comicId);
    const setCount = this.confirmModal.bookCount;
    this.confirmModal = null;
    this.executeRelease(comicId, item, true, setCount);
  }

  cancelRelease() {
    this.confirmModal = null;
  }

  private executeRelease(comicId: string, item: CartItem | undefined, isSetItem: boolean, setCount: number) {
    this.cartService.removeItem(comicId).subscribe({
      next: cart => {
        this.cart = cart;
      },
      error: () => this.error = 'Failed to remove item.'
    });
  }

  submitOrder() {
    this.submitting = true;
    this.cartService.submitOrder(this.customerNotes || undefined).subscribe({
      next: cart => { this.cart = cart; this.submitting = false; },
      error: (err) => {
        this.error = err?.error || 'Failed to submit order.';
        this.submitting = false;
      }
    });
  }

  get visibleItems() {
    return this.cart?.items.filter(i => !i.isSetContainer) ?? [];
  }

  get groupedRows(): CartRow[] {
    const all = this.cart?.items ?? [];
    const nonContainers = all.filter(i => !i.isSetContainer);
    const containers = all.filter(i => i.isSetContainer);
    const rows: CartRow[] = [];
    const seenGroups = new Set<number>();

    for (const item of nonContainers) {
      if (item.collectionGroup && item.collectionGroup > 0) {
        if (seenGroups.has(item.collectionGroup)) continue;
        seenGroups.add(item.collectionGroup);
        const setItems = nonContainers.filter(i => i.collectionGroup === item.collectionGroup);
        const container = containers.find(c => c.collectionGroup === item.collectionGroup);
        rows.push({
          type: 'set',
          collectionGroup: item.collectionGroup,
          items: setItems,
          totalPrice: setItems.reduce((sum, i) => sum + i.price, 0),
          claimedAt: setItems[0].claimedAt,
          removeId: setItems[0].comicId,
          isGraded: setItems.some(i => !!i.isGraded),
          containerTitle: container?.comicTitle,
          containerId: container?.comicId
        });
      } else {
        rows.push({
          type: 'single',
          collectionGroup: null,
          items: [item],
          totalPrice: item.price,
          claimedAt: item.claimedAt,
          removeId: item.comicId,
          isGraded: !!item.isGraded
        });
      }
    }
    return rows;
  }

  get cartTotal(): number {
    return this.visibleItems.reduce((sum, item) => sum + item.price, 0);
  }

  get shippingAmount(): number {
    // After submit, use persisted shippingCost; before submit, use live estimate
    if (this.cart?.shippingCost && this.cart.shippingCost > 0) return this.cart.shippingCost;
    return this.cart?.shippingEstimate?.estimatedCost ?? 0;
  }

  get discountedTotal(): number {
    return Math.max(0, this.cartTotal - (this.cart?.discountAmount ?? 0)) + this.shippingAmount;
  }

  discountedSetTotal(row: CartRow): number {
    return row.items.reduce((sum, item) => sum + this.discountedItemPrice(item), 0);
  }

  get showItemDiscounts(): boolean {
    return this.cart?.status !== 'OPEN' && (this.cart?.discountAmount ?? 0) > 0;
  }

  /** Apply rule-level exclusions to a cart item. Mirrors backend DiscountService logic. */
  private isItemExcluded(item: CartItem, rule: CartDiscount): boolean {
    if (rule.excludesSets && item.collectionGroup != null && item.collectionGroup > 0) return true;
    if (rule.excludesGraded && item.isGraded) return true;
    return false;
  }

  /**
   * Returns the eligible base price total for a given discount rule — i.e. the sum of prices
   * for all items that this rule applies to (after honoring its exclude flags).
   */
  private eligibleBaseForRule(rule: CartDiscount): number {
    return this.visibleItems
      .filter(i => !this.isItemExcluded(i, rule))
      .reduce((sum, i) => sum + i.price, 0);
  }

  /** True when the rule is a "Buy X get 1 free" type (description begins with "Buy "). */
  private isBuyXFreeRule(rule: CartDiscount): boolean {
    return rule.description.startsWith('Buy ');
  }

  /**
   * Computes the free-item set for every BUY_X_GET_ONE_FREE rule in the cart's breakdown
   * with cross-rule dedupe — mirrors {@code DiscountService.computeBuyXFreePicks} on the
   * backend so the per-row FREE badges line up with the backend's authoritative discount
   * amounts. Without dedupe, two rules whose backend-computed amounts both consume the
   * same $0.50 book would show only one FREE badge instead of two distinct ones.
   *
   * <p>Rules are processed in the breakdown's order; the backend produces the breakdown in
   * xBooks-ascending order so smaller-X rules already claim the cheapest items first, and
   * each later rule's {@code amount} reflects the leftover cheapest sum. We greedily
   * consume {@code rule.amount} from the cheapest items not yet freed.</p>
   */
  private computeBuyXFreeIds(): Map<string, Set<string>> {
    const result = new Map<string, Set<string>>();
    const breakdown = this.cart?.discountBreakdown;
    if (!breakdown || breakdown.length === 0) return result;

    const alreadyFreed = new Set<string>();
    for (const rule of breakdown) {
      if (!this.isBuyXFreeRule(rule)) continue;

      const eligible = this.visibleItems
        .filter(i => !this.isItemExcluded(i, rule)
          && !alreadyFreed.has(i.comicId))
        .slice()
        .sort((a, b) => a.price - b.price);

      const freeIds = new Set<string>();
      let remaining = Math.round(rule.amount * 100) / 100;
      for (const item of eligible) {
        if (remaining < 0.005) break;
        if (item.price <= remaining + 0.005) {
          freeIds.add(item.comicId);
          alreadyFreed.add(item.comicId);
          remaining = Math.round((remaining - item.price) * 100) / 100;
        }
      }
      result.set(rule.description, freeIds);
    }
    return result;
  }

  /**
   * Exact discounted price for one item.
   *
   * When discountBreakdown is present (orders submitted after this feature shipped), each rule's
   * savings are distributed based on the rule type:
   *   - BUY_X_GET_ONE_FREE: the cheapest eligible item(s) are fully free ($0.00); all others
   *     absorb nothing from this rule.
   *   - All other rules: savings are distributed proportionally among eligible items, so
   *     set members skip set-excluded rules.
   *
   * Falls back to a simple proportional spread for older orders that were submitted before
   * the breakdown field existed.
   */
  discountedItemPrice(item: CartItem): number {
    const breakdown = this.cart?.discountBreakdown;
    if (breakdown && breakdown.length > 0) {
      const buyXFreeMap = this.computeBuyXFreeIds();
      let totalItemDiscount = 0;
      for (const rule of breakdown) {
        if (this.isItemExcluded(item, rule)) continue;
        if (this.isBuyXFreeRule(rule)) {
          // Free items absorb their full price from this rule; all others absorb nothing.
          if (buyXFreeMap.get(rule.description)?.has(item.comicId)) {
            totalItemDiscount += item.price;
          }
          continue;
        }
        const base = this.eligibleBaseForRule(rule);
        if (base <= 0) continue;
        totalItemDiscount += (item.price / base) * rule.amount;
      }
      return Math.max(0, Math.round((item.price - totalItemDiscount) * 100) / 100);
    }

    // Fallback for orders without breakdown: spread total discount proportionally.
    const discount = this.cart?.discountAmount ?? 0;
    const base = this.visibleItems.reduce((sum, i) => sum + i.price, 0);
    if (discount <= 0 || base <= 0) return item.price;
    const factor = (base - discount) / base;
    if (factor <= 0) return 0;
    return Math.round(item.price * factor * 100) / 100;
  }

  get statusLabel(): string {
    switch (this.cart?.status) {
      case 'OPEN': return 'Open — add or remove items freely.';
      case 'FINALIZING': return 'Submitted — your order is with the seller.';
      case 'FINALIZED': return 'Finalized — awaiting fulfillment.';
      case 'FULFILLED': return 'Fulfilled — your order has been shipped!';
      default: return '';
    }
  }

  canRemove(): boolean {
    return this.cart?.status === 'OPEN' || this.cart?.status === 'FINALIZING';
  }

  canRemoveItem(comicId: string): boolean {
    return this.canRemove();
  }

  canSubmit(): boolean {
    return this.cart?.status === 'OPEN' &&
           this.visibleItems.length > 0;
  }

  canUnsubmit(): boolean {
    return this.cart?.status === 'FINALIZING' || this.cart?.status === 'FINALIZED';
  }

  get isPaid(): boolean {
    return this.paymentSuccess || this.cart?.paymentStatus === 'PAID';
  }

  onPaymentComplete() {
    this.showPaymentModal = false;
    this.paymentSuccess = true;
    this.loadCart();
  }

  onShippingAddressSaved(updatedCart: Cart) {
    this.cart = updatedCart;
  }

  onShippingPayNow(updatedCart: Cart) {
    this.cart = updatedCart;
    this.showShippingModal = false;
    this.showPaymentModal = true;
  }

  onPayLater() {
    this.payLaterAcknowledged = true;
  }

  unsubmit() {
    this.cartService.unsubmitMyOrder().subscribe({
      next: cart => { this.cart = cart; },
      error: () => this.error = 'Failed to unsubmit order.'
    });
  }

}
