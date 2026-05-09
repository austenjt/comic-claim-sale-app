import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User, ShippingAddress } from './user';
import { apiBase } from './auth.config';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private http: HttpClient) {}

  getPendingUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${apiBase}/users/pending`);
  }

  approveUser(id: string): Observable<{ userId: string; status: string }> {
    return this.http.post<{ userId: string; status: string }>(`${apiBase}/users/${id}/approve`, {});
  }

  getApprovedUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${apiBase}/users`);
  }

  suspendUser(id: string): Observable<any> {
    return this.http.post(`${apiBase}/users/${id}/suspend`, {}, { responseType: 'text' });
  }

  reactivateUser(id: string): Observable<any> {
    return this.http.post(`${apiBase}/users/${id}/reactivate`, {}, { responseType: 'text' });
  }

  updateProfile(name: string, shippingAddress: ShippingAddress | null, phone: string, notes: string,
                preferences: string, venmoHandle: string, paypalHandle: string, ebayUsername: string,
                cashAppHandle: string): Observable<User> {
    return this.http.put<User>(`${apiBase}/users/me`, {
      name, shippingAddress, phone, notes, preferences,
      venmoHandle, paypalHandle, ebayUsername, cashAppHandle
    });
  }

  updateProfileAddress(shippingAddress: ShippingAddress): Observable<User> {
    return this.http.put<User>(`${apiBase}/users/me`, { shippingAddress });
  }
}
