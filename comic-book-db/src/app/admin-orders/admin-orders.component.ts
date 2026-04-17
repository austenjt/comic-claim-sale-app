import { Component, OnInit } from '@angular/core';
import { CartService } from '../cart.service';
import { Cart, CartItem } from '../cart';
import { ArchivedOrder, ArchivedOrderItem } from '../archived-order';
import { ConfigService } from '../config.service';

interface AdminCartRow {
  type: 'single' | 'set';
  collectionGroup: number | null;
  items: CartItem[];
  totalPrice: number;
  claimedAt: string;
  removeId: string;
  wonViaBid: boolean;
  containerTitle?: string;
  containerId?: string;
}

@Component({
    selector: 'app-admin-orders',
    templateUrl: './admin-orders.component.html',
    styleUrls: ['./admin-orders.component.css'],
    standalone: false
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

  adminConfirmModal: {
    comicId: string;
    setTitle: string;
    bookCount: number;
  } | null = null;

  groupedRows(cart: Cart): AdminCartRow[] {
    const all = cart.items ?? [];
    const nonContainers = all.filter(i => !i.isSetContainer);
    const containers = all.filter(i => i.isSetContainer);
    const rows: AdminCartRow[] = [];
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
          wonViaBid: !!item.wonViaBid
        });
      }
    }
    return rows;
  }

  openAdminReleaseConfirm(row: AdminCartRow) {
    this.adminConfirmModal = {
      comicId: row.removeId,
      setTitle: row.containerTitle ?? `Set ${row.collectionGroup}`,
      bookCount: row.items.length
    };
  }

  confirmAdminRelease() {
    if (!this.adminConfirmModal) return;
    const id = this.adminConfirmModal.comicId;
    this.adminConfirmModal = null;
    this.unclaim(id);
  }

  cancelAdminRelease() {
    this.adminConfirmModal = null;
  }

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

  unsubmit(cart: Cart) {
    this.cartService.unsubmitOrder(cart.id).subscribe({
      next: () => { this.loadOrders(); this.loadOpenCarts(); },
      error: () => this.error = 'Failed to unsubmit order.'
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

  readonly paymentStatuses = ['UNPAID', 'PAID'];

  adminNoteDrafts: Record<string, string> = {};

  saveAdminNotes(cartId: string) {
    const notes = this.adminNoteDrafts[cartId] ?? null;
    this.cartService.updateAdminNotes(cartId, notes || null).subscribe({
      next: updated => {
        const order = this.orders.find(o => o.id === cartId);
        if (order) order.adminNotes = updated.adminNotes;
      },
      error: () => this.error = 'Failed to save admin notes.'
    });
  }

  saveArchivedAdminNotes(orderId: string) {
    const notes = this.adminNoteDrafts[orderId] ?? null;
    this.cartService.updateArchivedAdminNotes(orderId, notes || null).subscribe({
      next: updated => {
        const order = this.archivedOrders.find(o => o.id === orderId);
        if (order) order.adminNotes = updated.adminNotes;
      },
      error: () => this.archivedError = 'Failed to save admin notes.'
    });
  }

  initNoteDraft(id: string, existing: string | null | undefined): string {
    if (!(id in this.adminNoteDrafts)) {
      this.adminNoteDrafts[id] = existing ?? '';
    }
    return this.adminNoteDrafts[id];
  }

  setPaymentStatus(cartId: string, status: string) {
    this.cartService.updatePaymentStatus(cartId, status).subscribe({
      next: updated => {
        const order = this.orders.find(o => o.id === cartId);
        if (order) order.paymentStatus = updated.paymentStatus;
      },
      error: () => this.error = 'Failed to update payment status.'
    });
  }

  setArchivedPaymentStatus(orderId: string, status: string) {
    this.cartService.updateArchivedPaymentStatus(orderId, status).subscribe({
      next: updated => {
        const order = this.archivedOrders.find(o => o.id === orderId);
        if (order) order.paymentStatus = updated.paymentStatus;
      },
      error: () => this.archivedError = 'Failed to update payment status.'
    });
  }

  paymentLabel(status: string | null | undefined): string {
    const labels: Record<string, string> = { UNPAID: 'Unpaid', PAID: 'Paid' };
    return status ? (labels[status] ?? status) : 'Unpaid';
  }

  hasSetItems(order: ArchivedOrder): boolean {
    return order.items.some(i => i.collectionGroup != null && i.collectionGroup > 0);
  }

  itemsTotal(items: { price: number }[]): number {
    return items.reduce((sum, item) => sum + item.price, 0);
  }

  showItemDiscounts(cart: Cart): boolean {
    return (cart.discountAmount ?? 0) > 0 && cart.status !== 'OPEN';
  }

  showArchivedItemDiscounts(order: ArchivedOrder): boolean {
    return (order.discountAmount ?? 0) > 0;
  }

  discountedItemPrice(item: CartItem, cart: Cart): number {
    if (item.wonViaBid) return item.price;
    const discount = cart.discountAmount ?? 0;
    const excludeSets = cart.discountExcludesSets === true;
    const visibleNonBid = cart.items.filter(i => !i.isSetContainer && !i.wonViaBid
      && !(excludeSets && i.collectionGroup != null && i.collectionGroup > 0));
    const base = visibleNonBid.reduce((sum, i) => sum + i.price, 0);
    if (discount <= 0 || base <= 0) return item.price;
    const factor = Math.max(0, base - discount) / base;
    return Math.round(item.price * factor * 100) / 100;
  }

  discountedArchivedItemPrice(item: ArchivedOrderItem, order: ArchivedOrder): number {
    const discount = order.discountAmount ?? 0;
    const base = order.items.reduce((sum, i) => sum + i.price, 0);
    if (discount <= 0 || base <= 0) return item.price;
    const factor = Math.max(0, base - discount) / base;
    return Math.round(item.price * factor * 100) / 100;
  }

  cartTotal(cart: Cart): number {
    return this.itemsTotal(cart.items) + (cart.shippingCost ?? 0);
  }

  archivedTotal(order: ArchivedOrder): number {
    return this.itemsTotal(order.items) + (order.shippingCost ?? 0);
  }

  statusLabel(status: string): string {
    const labels: Record<string, string> = {
      OPEN: 'Open', FINALIZING: 'Submitted', FINALIZED: 'Finalized'
    };
    return labels[status] ?? status;
  }

  canFulfill(order: Cart): boolean {
    if (order.status === 'FINALIZED') return true;
    if (order.status === 'FINALIZING') return order.paymentStatus === 'PAID';
    return false;
  }

  fulfillTitle(order: Cart): string {
    if (this.canFulfill(order)) return '';
    if (order.status === 'FINALIZING') {
      return 'Mark the order as Paid to enable fulfillment';
    }
    return 'Order must be submitted before fulfillment';
  }
}
