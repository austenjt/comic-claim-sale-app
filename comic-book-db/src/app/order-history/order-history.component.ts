import { Component, OnInit } from '@angular/core';
import { CartService } from '../cart.service';
import { ArchivedOrder, ArchivedOrderItem } from '../archived-order';

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

  orderSubtotal(order: ArchivedOrder): number {
    return order.items.reduce((sum, item) => sum + item.price, 0);
  }

  orderTotal(order: ArchivedOrder): number {
    const shipping = order.shippingCost ?? 0;
    return Math.max(0, this.orderSubtotal(order) - (order.discountAmount ?? 0)) + shipping;
  }

  showItemDiscounts(order: ArchivedOrder): boolean {
    return (order.discountAmount ?? 0) > 0;
  }

  private discountableSubtotal(order: ArchivedOrder): number {
    return order.items.filter(i => !i.wonViaBid).reduce((sum, i) => sum + i.price, 0);
  }

  discountedItemPrice(order: ArchivedOrder, item: ArchivedOrderItem): number {
    if (item.wonViaBid) return item.price;
    const discount = order.discountAmount ?? 0;
    const base = this.discountableSubtotal(order);
    if (discount <= 0 || base <= 0) return item.price;
    const factor = Math.max(0, base - discount) / base;
    return Math.round(item.price * factor * 100) / 100;
  }
}
