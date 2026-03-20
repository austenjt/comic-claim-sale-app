import { Component, OnInit } from '@angular/core';
import { CartService } from '../cart.service';
import { ToastService } from '../toast.service';
import { ConfigService } from '../config.service';
import { Cart } from '../cart';

@Component({
  selector: 'app-cart',
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.css']
})
export class CartComponent implements OnInit {
  cart: Cart | null = null;
  loading = false;
  error = '';
  submitting = false;
  customerNotes = '';

  constructor(private cartService: CartService, private toastService: ToastService, public configService: ConfigService) {}

  ngOnInit() {
    this.loadCart();
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
          this.toastService.show(msg);
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

  get finalizeDeadline(): Date | null {
    if (!this.cart?.finalizeAfter) return null;
    return new Date(this.cart.finalizeAfter);
  }

  get statusLabel(): string {
    switch (this.cart?.status) {
      case 'OPEN': return 'Open — add or remove items freely.';
      case 'FINALIZING': return `Submitted — ${this.configService.finalizeHours}-hour review window in progress.`;
      case 'FINALIZED': return 'Finalized — awaiting seller fulfillment.';
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
    return this.cart?.status === 'OPEN' && this.visibleItems.length > 0;
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
