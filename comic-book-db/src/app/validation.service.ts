import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../environments/environment';

export interface NumberValidationResult {
  value: string;
  valid: boolean;
  asNumber: number | null;
  asSentinel: string | null;
  validSentinels: string[];
  message: string | null;
}

@Injectable({ providedIn: 'root' })
export class ValidationService {

  private baseUrl = environment.apiBase;

  constructor(private http: HttpClient) {}

  validateComicNumber(value: string): Observable<NumberValidationResult> {
    return this.http.get<NumberValidationResult>(
      `${this.baseUrl}/validateNumber?value=${encodeURIComponent(value)}`
    );
  }
}
