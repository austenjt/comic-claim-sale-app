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

  // In-memory fallback for environments where localStorage is blocked (e.g. Safari
  // with "Block All Cookies" or Private Browsing).  The token lives here for the
  // duration of the SPA session even if it could not be persisted to localStorage.
  private _memoryToken: string | null = null;

  constructor(private http: HttpClient) {}

  private safeGetStorage(key: string): string | null {
    try { return localStorage.getItem(key); } catch { return null; }
  }

  private safeSetStorage(key: string, value: string): void {
    try { localStorage.setItem(key, value); } catch { /* unavailable in some Safari modes */ }
  }

  private safeRemoveStorage(key: string): void {
    try { localStorage.removeItem(key); } catch { /* ignore */ }
  }

  login(email: string, pin: string): Observable<SessionInfo> {
    return this.http.post<SessionInfo>(`${this.apiBase}/users/login`, { email, pin }).pipe(
      tap(resp => {
        this._memoryToken = resp.token;        // always keep in memory first
        this.safeSetStorage(SESSION_KEY, resp.token);
        this.currentUser$.next(resp.user);
      })
    );
  }

  logout(): void {
    const token = this.getToken();
    if (token) {
      this.http.post(`${this.apiBase}/users/logout`, {}).subscribe({ error: () => {} });
    }
    this._memoryToken = null;
    this.safeRemoveStorage(SESSION_KEY);
    this.currentUser$.next(null);
  }

  loadSession(): void {
    const token = this.safeGetStorage(SESSION_KEY);
    if (!token) return;
    this._memoryToken = token;               // sync memory from storage on startup
    this.http.get<User>(`${this.apiBase}/users/me`).pipe(
      tap(user => this.currentUser$.next(user)),
      catchError(() => {
        this._memoryToken = null;
        this.safeRemoveStorage(SESSION_KEY);
        return of(null);
      })
    ).subscribe();
  }

  /** Returns the session token, preferring localStorage but falling back to the
   *  in-memory copy for Safari environments where localStorage is not available. */
  getToken(): string | null {
    return this.safeGetStorage(SESSION_KEY) ?? this._memoryToken;
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
