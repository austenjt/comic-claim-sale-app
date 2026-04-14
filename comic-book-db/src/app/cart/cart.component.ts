import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { CartService } from '../cart.service';
import { LogService } from '../log.service';
import { ConfigService } from '../config.service';
import { ComicService } from '../comic.service';
import { AuthService } from '../auth.service';
import { Cart, CartItem } from '../cart';
import { Comic } from '../comic';

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

    if (this.cart?.status === 'FINALIZING') {
      const msg = isSetItem
        ? `Are you sure you want to remove all ${setCount} books in this set from your established order?`
        : 'Are you sure you want to remove a book from an established order?';
      if (!window.confirm(msg)) return;
    } else if (isSetItem) {
      if (!window.confirm(`This will return all ${setCount} books in this set to available. Continue?`)) return;
    }

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

  get cartTotal(): number {
    return this.visibleItems.reduce((sum, item) => sum + item.price, 0);
  }

  get shippingAmount(): number {
    // After submit, use persisted shippingCost; before submit, use live estimate
    if (this.cart?.shippingCost && this.cart.shippingCost > 0) return this.cart.shippingCost;
    return this.cart?.shippingEstimate?.estimatedCost ?? 0;
  }

  get discountedTotal(): number {
    const shipping = this.cart?.status !== 'OPEN' ? this.shippingAmount : 0;
    return Math.max(0, this.cartTotal - (this.cart?.discountAmount ?? 0)) + shipping;
  }

  get showItemDiscounts(): boolean {
    return this.cart?.status !== 'OPEN' && (this.cart?.discountAmount ?? 0) > 0;
  }

  /** Total of only the non-bid-won items — the pool the discount is spread across. */
  private get discountableTotal(): number {
    return this.visibleItems
      .filter(i => !i.wonViaBid)
      .reduce((sum, i) => sum + i.price, 0);
  }

  /**
   * Returns the discounted price for a single item.
   * Items won via bid are never discounted.
   * The discount factor is computed only against non-bid items,
   * so bid-won items still count toward quantity thresholds on the backend
   * but don't absorb any of the discount pool.
   */
  discountedItemPrice(item: CartItem): number {
    if (item.wonViaBid) return item.price;
    const discount = this.cart?.discountAmount ?? 0;
    const base = this.discountableTotal;
    if (discount <= 0 || base <= 0) return item.price;
    const factor = Math.max(0, base - discount) / base;
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
