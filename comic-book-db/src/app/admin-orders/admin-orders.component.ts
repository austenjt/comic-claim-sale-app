import { Component, OnInit } from '@angular/core';
import { CartService } from '../cart.service';
import { Cart } from '../cart';
import { ArchivedOrder } from '../archived-order';

@Component({
  selector: 'app-admin-orders',
  templateUrl: './admin-orders.component.html',
  styleUrls: ['./admin-orders.component.css']
})
export class AdminOrdersComponent implements OnInit {
  orders: Cart[] = [];
  openCarts: Cart[] = [];
  archivedOrders: ArchivedOrder[] = [];
  loading = false;
  loadingOpen = false;
  loadingArchived = false;
  error = '';
  fulfilledCartId: string | null = null;

  constructor(private cartService: CartService) {}

  ngOnInit() {
    this.loadOrders();
    this.loadOpenCarts();
    this.loadArchivedOrders();
  }

  loadOrders() {
    this.loading = true;
    this.cartService.getAllOrders().subscribe({
      next: orders => { this.orders = orders; this.loading = false; },
      error: () => { this.error = 'Failed to load orders.'; this.loading = false; }
    });
  }

  openCartsError = '';
  archivedError = '';

  loadOpenCarts() {
    this.loadingOpen = true;
    this.openCartsError = '';
    this.cartService.getOpenCarts().subscribe({
      next: carts => { this.openCarts = carts; this.loadingOpen = false; },
      error: () => { this.openCartsError = 'Failed to load open carts.'; this.loadingOpen = false; }
    });
  }

  loadArchivedOrders() {
    this.loadingArchived = true;
    this.archivedError = '';
    this.cartService.getAllArchivedOrders().subscribe({
      next: orders => { this.archivedOrders = orders; this.loadingArchived = false; },
      error: () => { this.archivedError = 'Failed to load archived orders.'; this.loadingArchived = false; }
    });
  }

  pendingFulfillId: string | null = null;

  fulfill(cart: Cart) {
    if (this.pendingFulfillId !== cart.id) {
      this.pendingFulfillId = cart.id;
      this.loadOrders();
      return;
    }
    this.pendingFulfillId = null;
    this.cartService.fulfillOrder(cart.id).subscribe({
      next: () => {
        this.fulfilledCartId = cart.id;
        this.orders = this.orders.filter(o => o.id !== cart.id);
        this.loadArchivedOrders();
      },
      error: () => this.error = 'Failed to fulfill order.'
    });
  }

  unclaim(comicId: string) {
    this.cartService.adminUnclaim(comicId).subscribe({
      next: () => { this.loadOrders(); this.loadOpenCarts(); },
      error: () => this.error = 'Failed to release claim.'
    });
  }

  cartTotal(cart: Cart): number {
    return cart.items.reduce((sum, item) => sum + item.price, 0);
  }

  archivedTotal(order: ArchivedOrder): number {
    return order.items.reduce((sum, item) => sum + item.price, 0);
  }

  statusLabel(status: string): string {
    const labels: Record<string, string> = {
      OPEN: 'Open', FINALIZING: 'Submitted (24h)', FINALIZED: 'Finalized'
    };
    return labels[status] ?? status;
  }

  fulfillTitle(order: Cart): string {
    if (order.status === 'FINALIZED') return '';
    if (order.status === 'FINALIZING' && order.finalizeAfter) {
      const deadline = new Date(order.finalizeAfter);
      return `Still in 24h review window — unlocks ${deadline.toLocaleString()}`;
    }
    return 'Order must be finalized before fulfillment';
  }
}
