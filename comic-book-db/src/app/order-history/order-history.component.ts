import { Component, OnInit } from '@angular/core';
import { CartService } from '../cart.service';
import { ArchivedOrder, ArchivedOrderItem } from '../archived-order';

interface OrderHistoryRow {
  type: 'single' | 'set';
  collectionGroup: number | null;
  items: ArchivedOrderItem[];
  totalPrice: number;
  claimedAt: string;
  containerTitle?: string;
}

@Component({
    selector: 'app-order-history',
    templateUrl: './order-history.component.html',
    styleUrls: ['./order-history.component.css'],
    standalone: false
})
export class OrderHistoryComponent implements OnInit {
  orders: ArchivedOrder[] = [];
  loading = false;
  error = '';

  constructor(private cartService: CartService) {}

  ngOnInit() {
    this.loading = true;
    this.cartService.getOrderHistory().subscribe({
      next: orders => { this.orders = orders; this.loading = false; },
      error: () => { this.error = 'Failed to load order history.'; this.loading = false; }
    });
  }

  groupedRows(order: ArchivedOrder): OrderHistoryRow[] {
    const isContainer = (i: ArchivedOrderItem) => i.comicNumber === '#SET';
    const nonContainers = order.items.filter(i => !isContainer(i));
    const containers = order.items.filter(i => isContainer(i));
    const rows: OrderHistoryRow[] = [];
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

  bookCount(order: ArchivedOrder): number {
    return order.items.filter(i => i.comicNumber !== '#SET').length;
  }

  orderSubtotal(order: ArchivedOrder): number {
    return order.items.reduce((sum, item) => sum + item.price, 0);
  }

  orderTotal(order: ArchivedOrder): number {
    return Math.max(0, this.orderSubtotal(order) - (order.discountAmount ?? 0)) + (order.shippingCost ?? 0);
  }

  discountedSetTotal(order: ArchivedOrder, row: OrderHistoryRow): number {
    return row.items.reduce((sum, item) => sum + this.discountedItemPrice(order, item), 0);
  }

  showItemDiscounts(order: ArchivedOrder): boolean {
    return (order.discountAmount ?? 0) > 0;
  }

  private parseBreakdown(description: string): Array<{ amount: number; excludesSets: boolean; isBuyXFree: boolean }> {
    return description.split('; ').map(seg => {
      const amountMatch = seg.match(/\$-([\d.]+)/);
      return {
        amount: amountMatch ? parseFloat(amountMatch[1]) : 0,
        excludesSets: seg.includes('(sets excluded)'),
        isBuyXFree: seg.startsWith('Buy ')
      };
    }).filter(r => r.amount > 0);
  }

  private getFreeItemIds(rule: { amount: number; excludesSets: boolean }, order: ArchivedOrder): Set<string> {
    const eligible = order.items
      .filter(i => i.comicNumber !== '#SET' &&
        !i.wonViaBid &&
        i.price > 0 &&
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

  discountedItemPrice(order: ArchivedOrder, item: ArchivedOrderItem): number {
    if (item.wonViaBid) return item.price;

    if (order.discountDescription) {
      const breakdown = this.parseBreakdown(order.discountDescription);
      if (breakdown.length > 0) {
        const isSetMember = item.collectionGroup != null && item.collectionGroup > 0;
        let totalItemDiscount = 0;
        for (const rule of breakdown) {
          if (rule.excludesSets && isSetMember) continue;
          if (rule.isBuyXFree) {
            if (item.comicId && this.getFreeItemIds(rule, order).has(item.comicId)) {
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

    // Fallback for orders with no description.
    const discount = order.discountAmount ?? 0;
    const base = order.items.filter(i => i.comicNumber !== '#SET' && !i.wonViaBid).reduce((sum, i) => sum + i.price, 0);
    if (discount <= 0 || base <= 0) return item.price;
    const factor = Math.max(0, base - discount) / base;
    return Math.round(item.price * factor * 100) / 100;
  }
}
