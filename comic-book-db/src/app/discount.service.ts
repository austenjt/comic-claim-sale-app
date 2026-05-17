import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Discount } from './discount';
import { environment } from '../environments/environment';

@Injectable({ providedIn: 'root' })
export class DiscountService {
  private readonly apiBase = environment.apiBase;

  constructor(private http: HttpClient) {}

  getAll(): Observable<Discount[]> {
    return this.http.get<Discount[]>(`${this.apiBase}/discounts`);
  }

  create(discount: Partial<Discount>): Observable<Discount> {
    return this.http.post<Discount>(`${this.apiBase}/discounts`, discount);
  }

  update(discount: Discount): Observable<Discount> {
    return this.http.put<Discount>(`${this.apiBase}/discounts/${discount.id}`, discount);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiBase}/discounts/${id}`, { responseType: 'text' as 'json' });
  }
}
