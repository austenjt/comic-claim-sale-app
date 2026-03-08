import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User } from './user';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly apiBase = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';

  constructor(private http: HttpClient) {}

  register(name: string, email: string, address: string, phone: string, paymentNotes: string): Observable<any> {
    return this.http.post(`${this.apiBase}/users/register`, { name, email, address, phone, paymentNotes });
  }

  getPendingUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${this.apiBase}/users/pending`);
  }

  approveUser(id: string): Observable<{ pin: string; userId: string }> {
    return this.http.post<{ pin: string; userId: string }>(`${this.apiBase}/users/${id}/approve`, {});
  }

  getApprovedUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${this.apiBase}/users`);
  }

  resetPin(id: string): Observable<{ pin: string; userId: string }> {
    return this.http.post<{ pin: string; userId: string }>(`${this.apiBase}/users/${id}/reset-pin`, {});
  }

  suspendUser(id: string): Observable<any> {
    return this.http.post(`${this.apiBase}/users/${id}/suspend`, {}, { responseType: 'text' });
  }

  reactivateUser(id: string): Observable<any> {
    return this.http.post(`${this.apiBase}/users/${id}/reactivate`, {}, { responseType: 'text' });
  }

  updateProfile(name: string, address: string, phone: string, paymentNotes: string): Observable<User> {
    return this.http.put<User>(`${this.apiBase}/users/me`, { name, address, phone, paymentNotes });
  }
}
