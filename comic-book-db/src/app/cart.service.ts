import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Cart } from './cart';
import { ArchivedOrder } from './archived-order';
import { ShippingAddress } from './user';
import { environment } from '../environments/environment';

export interface ClaimNotification {
  eventType: string;
  comicId: string;
  comicTitle: string;
  comicNumber: string | null;
  userName: string;
  price: number;
  claimedAt: string;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly apiBase = environment.apiBase;

  constructor(private http: HttpClient) {}

  getMyCart(): Observable<Cart | null> {
    return this.http.get<Cart | null>(`${this.apiBase}/cart`);
  }

  addItem(comicId: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/cart/items`, { comicId });
  }

  addSet(containerId: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/cart/set`, { containerId });
  }

  removeItem(comicId: string): Observable<Cart> {
    return this.http.delete<Cart>(`${this.apiBase}/cart/items/${comicId}`);
  }

  submitOrder(customerNotes?: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/cart/submit`, { customerNotes: customerNotes || null });
  }

  saveShippingAddress(address: ShippingAddress): Observable<Cart> {
    return this.http.put<Cart>(`${this.apiBase}/cart/address`, { shippingAddress: address });
  }

  unsubmitMyOrder(): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/cart/unsubmit`, {});
  }

  getClaimedMap(): Observable<Record<string, string>> {
    return this.http.get<Record<string, string>>(`${this.apiBase}/cart/claimed-ids`);
  }

  getNotifications(): Observable<ClaimNotification[]> {
    return this.http.get<ClaimNotification[]>(`${this.apiBase}/notifications`);
  }

  getOrderHistory(): Observable<ArchivedOrder[]> {
    return this.http.get<ArchivedOrder[]>(`${this.apiBase}/cart/history`);
  }

  getAllArchivedOrders(): Observable<ArchivedOrder[]> {
    return this.http.get<ArchivedOrder[]>(`${this.apiBase}/orders/archived`);
  }

  resetDatabase(): Observable<string> {
    return this.http.post(`${this.apiBase}/reset`, {}, { responseType: 'text' });
  }

  getAllOrders(): Observable<Cart[]> {
    return this.http.get<Cart[]>(`${this.apiBase}/orders`);
  }

  getOpenCarts(): Observable<Cart[]> {
    return this.http.get<Cart[]>(`${this.apiBase}/orders/open`);
  }

  fulfillOrder(cartId: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/orders/${cartId}/fulfill`, {});
  }

  unsubmitOrder(cartId: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/orders/${cartId}/unsubmit`, {});
  }

  /**
   * Admin: re-run discount calculation on a FINALIZING cart and re-send the order-submitted
   * email. Use when a cart's stored discount snapshot is out of date relative to the current
   * discount logic (e.g. after deploying a fix to discount math).
   */
  refreshOrderDiscounts(cartId: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/orders/${cartId}/refresh-discounts`, {});
  }

  updateAdminNotes(cartId: string, adminNotes: string | null): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/orders/${cartId}/notes`, { adminNotes });
  }

  updateArchivedAdminNotes(orderId: string, adminNotes: string | null): Observable<ArchivedOrder> {
    return this.http.post<ArchivedOrder>(`${this.apiBase}/orders/archived/${orderId}/notes`, { adminNotes });
  }

  updatePaymentStatus(cartId: string, status: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/orders/${cartId}/payment`, { status });
  }

  updateShipping(cartId: string, shipped: boolean, trackingNumber: string | null): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/orders/${cartId}/shipping`, { shipped, trackingNumber });
  }

  updateArchivedPaymentStatus(orderId: string, status: string): Observable<ArchivedOrder> {
    return this.http.post<ArchivedOrder>(`${this.apiBase}/orders/archived/${orderId}/payment`, { status });
  }

  updateArchivedShipping(orderId: string, shipped: boolean, trackingNumber: string | null): Observable<ArchivedOrder> {
    return this.http.post<ArchivedOrder>(`${this.apiBase}/orders/archived/${orderId}/shipping`, { shipped, trackingNumber });
  }

  adminUnclaim(comicId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiBase}/orders/claim/${comicId}`, { responseType: 'text' as 'json' });
  }

  deleteArchivedOrder(orderId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiBase}/orders/archived/${orderId}`, { responseType: 'text' as 'json' });
  }

  fullDeleteArchivedOrder(orderId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiBase}/orders/archived/${orderId}/full`, { responseType: 'text' as 'json' });
  }

  awardComic(comicId: string, userId: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/awards`, { comicId, userId });
  }

  addTradeItem(comicId: string, grade: number): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/cart/trade`, { comicId, grade });
  }

  markTradeReceived(cartId: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/orders/${cartId}/receive-trade`, {});
  }
}
