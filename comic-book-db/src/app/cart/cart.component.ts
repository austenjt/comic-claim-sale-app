import { Component, OnInit } from '@angular/core';
import { CartService } from '../cart.service';
import { ToastService } from '../toast.service';
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

  constructor(private cartService: CartService, private toastService: ToastService) {}

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
    if (this.cart?.status === 'FINALIZING') {
      const confirmed = window.confirm('Are you sure you want to remove a book from an established order?');
      if (!confirmed) return;
    }
    const item = this.cart?.items.find(i => i.comicId === comicId);
    this.cartService.removeItem(comicId).subscribe({
      next: cart => {
        this.cart = cart;
        if (item) {
          const numPart = item.comicNumber ? ` ${item.comicNumber}` : '';
          this.toastService.show(`"${item.comicTitle}${numPart}" Returned to sale`);
        }
      },
      error: () => this.error = 'Failed to remove item.'
    });
  }

  submitOrder() {
    this.submitting = true;
    this.cartService.submitOrder().subscribe({
      next: cart => { this.cart = cart; this.submitting = false; },
      error: (err) => {
        this.error = err?.error || 'Failed to submit order.';
        this.submitting = false;
      }
    });
  }

  get cartTotal(): number {
    return this.cart?.items.reduce((sum, item) => sum + item.price, 0) ?? 0;
  }

  get discountedTotal(): number {
    return Math.max(0, this.cartTotal - (this.cart?.discountAmount ?? 0));
  }

  get finalizeDeadline(): Date | null {
    if (!this.cart?.finalizeAfter) return null;
    return new Date(this.cart.finalizeAfter);
  }

  get statusLabel(): string {
    switch (this.cart?.status) {
      case 'OPEN': return 'Open — add or remove items freely.';
      case 'FINALIZING': return 'Submitted — 24-hour review window in progress.';
      case 'FINALIZED': return 'Finalized — awaiting seller fulfillment.';
      case 'FULFILLED': return 'Fulfilled — your order has been shipped!';
      default: return '';
    }
  }

  canRemove(): boolean {
    return this.cart?.status === 'OPEN' || this.cart?.status === 'FINALIZING';
  }

  canSubmit(): boolean {
    return this.cart?.status === 'OPEN' && (this.cart?.items?.length ?? 0) > 0;
  }

}
