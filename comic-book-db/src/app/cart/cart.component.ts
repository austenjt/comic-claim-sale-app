import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { CartService } from '../cart.service';
import { LogService } from '../log.service';
import { ConfigService } from '../config.service';
import { ComicService } from '../comic.service';
import { AuthService } from '../auth.service';
import { Cart, CartDiscount, CartItem } from '../cart';
import { Comic } from '../comic';

interface CartRow {
  type: 'single' | 'set';
  collectionGroup: number | null;
  items: CartItem[];
  totalPrice: number;
  claimedAt: string;
  removeId: string;
  wonViaBid: boolean;
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

  confirmModal: {
    comicId: string;
    setTitle: string;
    bookCount: number;
    isFinalizing: boolean;
  } | null = null;

  private activeBidComics: Comic[] = [];
  private claimSub!: Subscription;

  constructor(
    private cartService: CartService,
    private logService: LogService,
    public configService: ConfigService,
    private comicService: ComicService,
    public auth: AuthService
  ) {}

  ngOnInit() {
    this.loadCart();
    this.refreshActiveBids();
    this.claimSub = this.logService.newClaimEvent$.subscribe(() => this.refreshActiveBids());
  }

  ngOnDestroy() {
    this.claimSub?.unsubscribe();
  }

  private refreshActiveBids(): void {
    if (!this.configService.biddingMode) return;
    this.comicService.getDashboardPage(1, 'bidding-first', false, true).subscribe({
      next: resp => { this.activeBidComics = resp.items; },
      error: () => {}
    });
  }

  private bidSecondsRemaining(comic: Comic): number {
    if (!comic.bidStartedAt) return 0;
    const endsAt = new Date(comic.bidStartedAt).getTime() +
                   this.configService.biddingCycleMins * 60000;
    return Math.max(0, Math.floor((endsAt - Date.now()) / 1000));
  }

  get myActiveBidLeads(): Comic[] {
    const myName = this.auth.currentUser$.value?.name;
    if (!myName) return [];
    return this.activeBidComics.filter(c =>
      c.currentBidderName === myName && this.bidSecondsRemaining(c) > 0
    );
  }

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
        if (item) {
          const msg = isSetItem
            ? `"${item.comicTitle}" set (${setCount} books) returned to available.`
            : `"${item.comicTitle}${item.comicNumber ? ' ' + item.comicNumber : ''}" Returned to sale`;
          this.logService.log(msg);
        }
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
          wonViaBid: setItems.some(i => !!i.wonViaBid),
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
          wonViaBid: !!item.wonViaBid,
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
    if (rule.excludesAuctions && item.wonViaBid) return true;
    if (rule.excludesGraded && item.isGraded) return true;
    return false;
  }

  /**
   * Returns the eligible base price total for a given discount rule — i.e. the sum of prices
   * for all non-bid items that this rule applies to (after honoring its exclude flags).
   */
  private eligibleBaseForRule(rule: CartDiscount): number {
    return this.visibleItems
      .filter(i => !i.wonViaBid && !this.isItemExcluded(i, rule))
      .reduce((sum, i) => sum + i.price, 0);
  }

  /** True when the rule is a "Buy X get 1 free" type (description begins with "Buy "). */
  private isBuyXFreeRule(rule: CartDiscount): boolean {
    return rule.description.startsWith('Buy ');
  }

  /**
   * Identifies which item IDs are free under a BUY_X_GET_ONE_FREE rule.
   * Mirrors the backend logic: sort eligible items by price ascending, then greedily pick the
   * cheapest until their cumulative price equals rule.amount.
   */
  private getFreeItemIds(rule: CartDiscount): Set<string> {
    const eligible = this.visibleItems
      .filter(i => !i.wonViaBid && !this.isItemExcluded(i, rule))
      .slice()
      .sort((a, b) => a.price - b.price);

    const freeIds = new Set<string>();
    let remaining = Math.round(rule.amount * 100) / 100;
    for (const item of eligible) {
      if (remaining < 0.005) break;
      if (item.price <= remaining + 0.005) {
        freeIds.add(item.comicId);
        remaining = Math.round((remaining - item.price) * 100) / 100;
      }
    }
    return freeIds;
  }

  /**
   * Exact discounted price for one item.
   *
   * When discountBreakdown is present (orders submitted after this feature shipped), each rule's
   * savings are distributed based on the rule type:
   *   - BUY_X_GET_ONE_FREE: the cheapest eligible item(s) are fully free ($0.00); all others
   *     absorb nothing from this rule (bid items always return their original price).
   *   - All other rules: savings are distributed proportionally among eligible items, so
   *     set members skip set-excluded rules.
   *
   * Falls back to a simple proportional spread across all non-bid items for older orders that
   * were submitted before the breakdown field existed.
   */
  discountedItemPrice(item: CartItem): number {
    if (item.wonViaBid) return item.price;

    const breakdown = this.cart?.discountBreakdown;
    if (breakdown && breakdown.length > 0) {
      let totalItemDiscount = 0;
      for (const rule of breakdown) {
        if (this.isItemExcluded(item, rule)) continue;
        if (this.isBuyXFreeRule(rule)) {
          // Free items absorb their full price from this rule; all others absorb nothing.
          if (this.getFreeItemIds(rule).has(item.comicId)) {
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
    const base = this.visibleItems.filter(i => !i.wonViaBid).reduce((sum, i) => sum + i.price, 0);
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
    if (!this.canRemove()) return false;
    const item = this.cart?.items.find(i => i.comicId === comicId);
    return !item?.wonViaBid;
  }

  canSubmit(): boolean {
    return this.cart?.status === 'OPEN' &&
           this.visibleItems.length > 0 &&
           this.myActiveBidLeads.length === 0;
  }

  canUnsubmit(): boolean {
    return this.cart?.status === 'FINALIZING' || this.cart?.status === 'FINALIZED';
  }

  unsubmit() {
    this.cartService.unsubmitMyOrder().subscribe({
      next: cart => { this.cart = cart; },
      error: () => this.error = 'Failed to unsubmit order.'
    });
  }

}
