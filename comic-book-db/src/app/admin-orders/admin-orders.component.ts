import { Component, OnInit } from '@angular/core';
import { CartService } from '../cart.service';
import { Cart, CartDiscount, CartItem } from '../cart';
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

interface ArchivedCartRow {
  type: 'single' | 'set';
  collectionGroup: number | null;
  items: ArchivedOrderItem[];
  totalPrice: number;
  claimedAt: string;
  containerTitle?: string;
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

  trackByOrderId(_index: number, order: Cart): string { return order.id; }
  trackByArchivedOrderId(_index: number, order: ArchivedOrder): string { return order.id; }

  fulfill(cart: Cart) {
    if (this.pendingFulfillId !== cart.id) {
      this.pendingFulfillId = cart.id;
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
  trackingDrafts: Record<string, string> = {};
  archivedTrackingDrafts: Record<string, string> = {};

  // Per-order edit mode: true = editing, false = viewing saved value
  notesEditing: Record<string, boolean> = {};
  trackingEditing: Record<string, boolean> = {};

  isNotesEditing(orderId: string, adminNotes: string | null | undefined): boolean {
    if (orderId in this.notesEditing) return this.notesEditing[orderId];
    return !adminNotes; // no saved note → start in edit mode
  }

  isTrackingEditing(orderId: string, trackingNumber: string | null | undefined): boolean {
    if (orderId in this.trackingEditing) return this.trackingEditing[orderId];
    return !trackingNumber; // no saved tracking → start in edit mode
  }

  saveAdminNotes(cartId: string) {
    const notes = this.adminNoteDrafts[cartId] ?? null;
    this.cartService.updateAdminNotes(cartId, notes || null).subscribe({
      next: updated => {
        const order = this.orders.find(o => o.id === cartId);
        if (order) order.adminNotes = updated.adminNotes;
        this.notesEditing[cartId] = false;
      },
      error: () => this.error = 'Failed to save seller notes.'
    });
  }

  saveArchivedAdminNotes(orderId: string) {
    const notes = this.adminNoteDrafts[orderId] ?? null;
    this.cartService.updateArchivedAdminNotes(orderId, notes || null).subscribe({
      next: updated => {
        const order = this.archivedOrders.find(o => o.id === orderId);
        if (order) order.adminNotes = updated.adminNotes;
        this.notesEditing[orderId] = false;
      },
      error: () => this.archivedError = 'Failed to save seller notes.'
    });
  }

  initNoteDraft(id: string, existing: string | null | undefined): string {
    if (!(id in this.adminNoteDrafts)) {
      this.adminNoteDrafts[id] = existing ?? '';
    }
    return this.adminNoteDrafts[id];
  }

  togglePaid(order: Cart) {
    const newStatus = order.paymentStatus === 'PAID' ? 'UNPAID' : 'PAID';
    this.cartService.updatePaymentStatus(order.id, newStatus).subscribe({
      next: updated => {
        const o = this.orders.find(o => o.id === order.id);
        if (o) o.paymentStatus = updated.paymentStatus;
      },
      error: () => this.error = 'Failed to update payment status.'
    });
  }

  toggleShipped(order: Cart) {
    const newShipped = !order.shipped;
    const tracking = this.trackingDrafts[order.id] ?? order.trackingNumber ?? null;
    this.cartService.updateShipping(order.id, newShipped, tracking || null).subscribe({
      next: updated => {
        const o = this.orders.find(o => o.id === order.id);
        if (o) { o.shipped = updated.shipped; o.trackingNumber = updated.trackingNumber; }
      },
      error: () => this.error = 'Failed to update shipping status.'
    });
  }

  saveTracking(order: Cart) {
    const tracking = this.trackingDrafts[order.id] ?? order.trackingNumber ?? null;
    this.cartService.updateShipping(order.id, true, tracking || null).subscribe({
      next: updated => {
        const o = this.orders.find(o => o.id === order.id);
        if (o) o.trackingNumber = updated.trackingNumber;
        this.trackingEditing[order.id] = false;
      },
      error: () => this.error = 'Failed to save tracking number.'
    });
  }

  initTrackingDraft(cartId: string, existing: string | null | undefined): string {
    if (!(cartId in this.trackingDrafts)) {
      this.trackingDrafts[cartId] = existing ?? '';
    }
    return this.trackingDrafts[cartId];
  }

  toggleArchivedPaid(order: ArchivedOrder) {
    const newStatus = order.paymentStatus === 'PAID' ? 'UNPAID' : 'PAID';
    this.cartService.updateArchivedPaymentStatus(order.id, newStatus).subscribe({
      next: updated => {
        const o = this.archivedOrders.find(o => o.id === order.id);
        if (o) o.paymentStatus = updated.paymentStatus;
      },
      error: () => this.archivedError = 'Failed to update payment status.'
    });
  }

  toggleArchivedShipped(order: ArchivedOrder) {
    const newShipped = !order.shipped;
    const tracking = this.archivedTrackingDrafts[order.id] ?? order.trackingNumber ?? null;
    this.cartService.updateArchivedShipping(order.id, newShipped, tracking || null).subscribe({
      next: updated => {
        const o = this.archivedOrders.find(o => o.id === order.id);
        if (o) { o.shipped = updated.shipped; o.trackingNumber = updated.trackingNumber; }
      },
      error: () => this.archivedError = 'Failed to update shipping status.'
    });
  }

  saveArchivedTracking(order: ArchivedOrder) {
    const tracking = this.archivedTrackingDrafts[order.id] ?? order.trackingNumber ?? null;
    this.cartService.updateArchivedShipping(order.id, true, tracking || null).subscribe({
      next: updated => {
        const o = this.archivedOrders.find(o => o.id === order.id);
        if (o) o.trackingNumber = updated.trackingNumber;
        this.trackingEditing[order.id] = false;
      },
      error: () => this.archivedError = 'Failed to save tracking number.'
    });
  }

  initArchivedTrackingDraft(orderId: string, existing: string | null | undefined): string {
    if (!(orderId in this.archivedTrackingDrafts)) {
      this.archivedTrackingDrafts[orderId] = existing ?? '';
    }
    return this.archivedTrackingDrafts[orderId];
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

  visibleCartItems(cart: Cart): CartItem[] {
    return cart.items.filter(i => !i.isSetContainer);
  }

  discountedSetTotal(row: AdminCartRow, cart: Cart): number {
    return row.items.reduce((sum, item) => sum + this.discountedItemPrice(item, cart), 0);
  }

  discountedArchivedSetTotal(row: ArchivedCartRow, order: ArchivedOrder): number {
    return row.items.reduce((sum, item) => sum + this.discountedArchivedItemPrice(item, order), 0);
  }

  showItemDiscounts(cart: Cart): boolean {
    return (cart.discountAmount ?? 0) > 0 && cart.status !== 'OPEN';
  }

  showArchivedItemDiscounts(order: ArchivedOrder): boolean {
    return (order.discountAmount ?? 0) > 0;
  }

  private eligibleBaseForRule(rule: CartDiscount, cart: Cart): number {
    return this.visibleCartItems(cart)
      .filter(i => !i.wonViaBid &&
        !(rule.excludesSets && i.collectionGroup != null && i.collectionGroup > 0))
      .reduce((sum, i) => sum + i.price, 0);
  }

  private isBuyXFreeRule(rule: CartDiscount): boolean {
    return rule.description.startsWith('Buy ');
  }

  private getFreeItemIds(rule: CartDiscount, cart: Cart): Set<string> {
    const eligible = this.visibleCartItems(cart)
      .filter(i => !i.wonViaBid &&
        !(rule.excludesSets && i.collectionGroup != null && i.collectionGroup > 0))
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

  discountedItemPrice(item: CartItem, cart: Cart): number {
    if (item.wonViaBid) return item.price;

    const breakdown = cart.discountBreakdown;
    if (breakdown && breakdown.length > 0) {
      const isSetMember = item.collectionGroup != null && item.collectionGroup > 0;
      let totalItemDiscount = 0;
      for (const rule of breakdown) {
        if (rule.excludesSets && isSetMember) continue;
        if (this.isBuyXFreeRule(rule)) {
          if (this.getFreeItemIds(rule, cart).has(item.comicId)) {
            totalItemDiscount += item.price;
          }
          continue;
        }
        const base = this.eligibleBaseForRule(rule, cart);
        if (base <= 0) continue;
        totalItemDiscount += (item.price / base) * rule.amount;
      }
      return Math.max(0, Math.round((item.price - totalItemDiscount) * 100) / 100);
    }

    const discount = cart.discountAmount ?? 0;
    const excludeSets = cart.discountExcludesSets === true;
    const visibleNonBid = this.visibleCartItems(cart).filter(i => !i.wonViaBid
      && !(excludeSets && i.collectionGroup != null && i.collectionGroup > 0));
    const base = visibleNonBid.reduce((sum, i) => sum + i.price, 0);
    if (discount <= 0 || base <= 0) return item.price;
    const factor = Math.max(0, base - discount) / base;
    return Math.round(item.price * factor * 100) / 100;
  }

  orderDiscountedTotal(order: Cart): number {
    return Math.max(0, this.cartTotal(order) - (order.discountAmount || 0)) + (order.shippingCost || 0);
  }

  /**
   * Parses discountDescription into per-rule descriptors, extracting the savings amount,
   * whether sets were excluded, and whether the rule is BUY_X_GET_ONE_FREE.
   * Description format (rules joined by "; "):
   *   "50% off ($-9.88) (sets excluded)"
   *   "5% off (over 20 books, $-16.94)"
   *   "Buy 10 get 1 free (1 free, $-0.75) (sets excluded)"
   */
  private parseArchivedBreakdown(description: string): Array<{ amount: number; excludesSets: boolean; isBuyXFree: boolean }> {
    return description.split('; ').map(seg => {
      const amountMatch = seg.match(/\$-([\d.]+)/);
      return {
        amount: amountMatch ? parseFloat(amountMatch[1]) : 0,
        excludesSets: seg.includes('(sets excluded)'),
        isBuyXFree: seg.startsWith('Buy ')
      };
    }).filter(r => r.amount > 0);
  }

  private getArchivedFreeItemIds(rule: { amount: number; excludesSets: boolean }, order: ArchivedOrder): Set<string> {
    const eligible = order.items
      .filter(i => i.comicNumber !== '#SET' &&   // exclude set containers
        !i.wonViaBid &&                           // exclude bid-won items
        i.price > 0 &&                            // exclude $0 items (containers, awarded)
        !!i.comicId &&
        !(rule.excludesSets && i.collectionGroup != null && i.collectionGroup > 0))
      .slice()
      .sort((a, b) => a.price - b.price);

    const freeIds = new Set<string>();
    let remaining = Math.round(rule.amount * 100) / 100;
    for (const item of eligible) {
      if (remaining < 0.005) break;
      if (item.price <= remaining + 0.005) {
        freeIds.add(item.comicId!);
        remaining = Math.round((remaining - item.price) * 100) / 100;
      }
    }
    return freeIds;
  }

  discountedArchivedItemPrice(item: ArchivedOrderItem, order: ArchivedOrder): number {
    if (item.wonViaBid) return item.price;

    if (order.discountDescription) {
      const breakdown = this.parseArchivedBreakdown(order.discountDescription);
      if (breakdown.length > 0) {
        const isSetMember = item.collectionGroup != null && item.collectionGroup > 0;
        let totalItemDiscount = 0;
        for (const rule of breakdown) {
          if (rule.excludesSets && isSetMember) continue;
          if (rule.isBuyXFree) {
            if (item.comicId && this.getArchivedFreeItemIds(rule, order).has(item.comicId)) {
              totalItemDiscount += item.price;
            }
            continue;
          }
          const base = order.items
            .filter(i => i.comicNumber !== '#SET' &&
              !i.wonViaBid &&
              !(rule.excludesSets && i.collectionGroup != null && i.collectionGroup > 0))
            .reduce((sum, i) => sum + i.price, 0);
          if (base <= 0) continue;
          totalItemDiscount += (item.price / base) * rule.amount;
        }
        return Math.max(0, Math.round((item.price - totalItemDiscount) * 100) / 100);
      }
    }

    // Fallback for orders with no description: spread total proportionally.
    const discount = order.discountAmount ?? 0;
    const base = order.items.filter(i => !i.wonViaBid).reduce((sum, i) => sum + i.price, 0);
    if (discount <= 0 || base <= 0) return item.price;
    const factor = Math.max(0, base - discount) / base;
    return Math.round(item.price * factor * 100) / 100;
  }

  groupedArchivedRows(order: ArchivedOrder): ArchivedCartRow[] {
    const isContainer = (i: ArchivedOrderItem) => i.comicNumber === '#SET';
    const nonContainers = order.items.filter(i => !isContainer(i));
    const containers = order.items.filter(i => isContainer(i));
    const rows: ArchivedCartRow[] = [];
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
          containerTitle: container?.comicTitle
        });
      } else {
        rows.push({
          type: 'single',
          collectionGroup: null,
          items: [item],
          totalPrice: item.price,
          claimedAt: item.claimedAt
        });
      }
    }
    return rows;
  }

  archivedBookCount(order: ArchivedOrder): number {
    return order.items.filter(i => i.comicNumber !== '#SET').length;
  }

  archivedOrderSubtotal(order: ArchivedOrder): number {
    return order.items.reduce((sum, i) => sum + i.price, 0);
  }

  archivedOrderTotal(order: ArchivedOrder): number {
    return Math.max(0, this.archivedOrderSubtotal(order) - (order.discountAmount || 0)) + (order.shippingCost || 0);
  }

  cartTotal(cart: Cart): number {
    return this.itemsTotal(this.visibleCartItems(cart));
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
    if (order.status !== 'FINALIZING' && order.status !== 'FINALIZED') return false;
    return order.paymentStatus === 'PAID' && order.shipped === true;
  }

  fulfillTitle(order: Cart): string {
    if (this.canFulfill(order)) return '';
    if (order.status !== 'FINALIZING' && order.status !== 'FINALIZED') {
      return 'Order must be submitted before fulfillment';
    }
    if (order.paymentStatus !== 'PAID') return 'Mark the order as Paid to enable fulfillment';
    if (!order.shipped) return 'Mark the order as Shipped to enable fulfillment';
    return '';
  }
}
