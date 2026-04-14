import { Component, OnInit } from '@angular/core';
import { CartService } from '../cart.service';
import { ArchivedOrder } from '../archived-order';

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

  orderTotal(order: ArchivedOrder): number {
    return order.items.reduce((sum, item) => sum + item.price, 0);
  }
}
