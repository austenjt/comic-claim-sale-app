import { Component, OnInit } from '@angular/core';
import { CartService } from '../cart.service';
import { Cart } from '../cart';
import { ArchivedOrder } from '../archived-order';
import { ConfigService } from '../config.service';

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

  constructor(private cartService: CartService, private configService: ConfigService) {}

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
  pendingDeleteId: string | null = null;
  pendingFullDeleteId: string | null = null;

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

  deleteArchivedOrder(order: ArchivedOrder) {
    if (this.pendingDeleteId !== order.id) {
      this.pendingDeleteId = order.id;
      this.pendingFullDeleteId = null;
      return;
    }
    this.pendingDeleteId = null;
    this.cartService.deleteArchivedOrder(order.id).subscribe({
      next: () => { this.archivedOrders = this.archivedOrders.filter(o => o.id !== order.id); },
      error: () => { this.archivedError = 'Failed to delete archived order.'; }
    });
  }

  fullDeleteArchivedOrder(order: ArchivedOrder) {
    if (this.pendingFullDeleteId !== order.id) {
      this.pendingFullDeleteId = order.id;
      this.pendingDeleteId = null;
      return;
    }
    this.pendingFullDeleteId = null;
    this.cartService.fullDeleteArchivedOrder(order.id).subscribe({
      next: () => { this.archivedOrders = this.archivedOrders.filter(o => o.id !== order.id); },
      error: () => { this.archivedError = 'Failed to fully delete archived order.'; }
    });
  }

  hasSetItems(order: ArchivedOrder): boolean {
    return order.items.some(i => i.collectionGroup != null && i.collectionGroup > 0);
  }

  cartTotal(cart: Cart): number {
    return cart.items.reduce((sum, item) => sum + item.price, 0);
  }

  archivedTotal(order: ArchivedOrder): number {
    return order.items.reduce((sum, item) => sum + item.price, 0);
  }

  statusLabel(status: string): string {
    const hours = this.configService.finalizeHours;
    const labels: Record<string, string> = {
      OPEN: 'Open', FINALIZING: `Submitted (${hours}h)`, FINALIZED: 'Finalized'
    };
    return labels[status] ?? status;
  }

  canFulfill(order: Cart): boolean {
    if (order.status === 'FINALIZED') return true;
    if (order.status === 'FINALIZING' && order.finalizeAfter) {
      return Date.now() > new Date(order.finalizeAfter).getTime();
    }
    return false;
  }

  fulfillTitle(order: Cart): string {
    if (this.canFulfill(order)) return '';
    if (order.status === 'FINALIZING' && order.finalizeAfter) {
      const deadline = new Date(order.finalizeAfter);
      const hours = this.configService.finalizeHours;
      return `Still in ${hours}h review window — unlocks ${deadline.toLocaleString()}`;
    }
    return 'Order must be finalized before fulfillment';
  }
}
