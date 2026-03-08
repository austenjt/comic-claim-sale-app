import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { User, SessionInfo } from './user';

const SESSION_KEY = 'session_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly apiBase = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';
  currentUser$ = new BehaviorSubject<User | null>(null);

  constructor(private http: HttpClient) {}

  login(email: string, pin: string): Observable<SessionInfo> {
    return this.http.post<SessionInfo>(`${this.apiBase}/users/login`, { email, pin }).pipe(
      tap(resp => {
        localStorage.setItem(SESSION_KEY, resp.token);
        this.currentUser$.next(resp.user);
      })
    );
  }

  logout(): void {
    const token = localStorage.getItem(SESSION_KEY);
    if (token) {
      this.http.post(`${this.apiBase}/users/logout`, {}).subscribe({ error: () => {} });
    }
    localStorage.removeItem(SESSION_KEY);
    this.currentUser$.next(null);
  }

  loadSession(): void {
    const token = localStorage.getItem(SESSION_KEY);
    if (!token) return;
    this.http.get<User>(`${this.apiBase}/users/me`).pipe(
      tap(user => this.currentUser$.next(user)),
      catchError(() => {
        localStorage.removeItem(SESSION_KEY);
        return of(null);
      })
    ).subscribe();
  }

  getToken(): string | null {
    return localStorage.getItem(SESSION_KEY);
  }

  isLoggedIn(): boolean {
    return this.currentUser$.value !== null;
  }

  isAdmin(): boolean {
    return this.currentUser$.value?.isAdmin === true;
  }

  isApproved(): boolean {
    const user = this.currentUser$.value;
    if (!user) return false;
    return user.isAdmin || user.status === 'APPROVED';
  }
}
