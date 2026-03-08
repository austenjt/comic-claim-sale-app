import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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

  private baseUrl = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';

  constructor(private http: HttpClient) {}

  validateComicNumber(value: string): Observable<NumberValidationResult> {
    return this.http.get<NumberValidationResult>(
      `${this.baseUrl}/validateNumber?value=${encodeURIComponent(value)}`
    );
  }
}
