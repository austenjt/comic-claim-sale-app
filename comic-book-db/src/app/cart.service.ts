import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Cart } from './cart';
import { Comic } from './comic';
import { ArchivedOrder } from './archived-order';

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
  private readonly apiBase = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';

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

  updateAdminNotes(cartId: string, adminNotes: string | null): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/orders/${cartId}/notes`, { adminNotes });
  }

  updateArchivedAdminNotes(orderId: string, adminNotes: string | null): Observable<ArchivedOrder> {
    return this.http.post<ArchivedOrder>(`${this.apiBase}/orders/archived/${orderId}/notes`, { adminNotes });
  }

  updatePaymentStatus(cartId: string, status: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/orders/${cartId}/payment`, { status });
  }

  updateArchivedPaymentStatus(orderId: string, status: string): Observable<ArchivedOrder> {
    return this.http.post<ArchivedOrder>(`${this.apiBase}/orders/archived/${orderId}/payment`, { status });
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

  openBid(comicId: string, startingBid?: number): Observable<Comic> {
    const body: any = { comicId };
    if (startingBid != null) body.startingBid = startingBid;
    return this.http.post<Comic>(`${this.apiBase}/bid/open`, body);
  }

  cancelBid(comicId: string): Observable<Comic> {
    return this.http.post<Comic>(`${this.apiBase}/bid/cancel`, { comicId });
  }

  startBid(comicId: string): Observable<Comic> {
    return this.http.post<Comic>(`${this.apiBase}/bid/start`, { comicId });
  }

  placeBid(comicId: string, amount: number): Observable<Comic> {
    return this.http.post<Comic>(`${this.apiBase}/bid`, { comicId, amount });
  }

  finalizeBid(comicId: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiBase}/bid/finalize`, { comicId });
  }
}
