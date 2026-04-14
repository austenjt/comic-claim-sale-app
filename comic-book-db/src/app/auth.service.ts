import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { MsalService, MsalBroadcastService } from '@azure/msal-angular';
import { InteractionStatus } from '@azure/msal-browser';
import { filter } from 'rxjs/operators';
import { User } from './user';
import { apiBase, clientId } from './auth.config';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly meUrl = `${apiBase}/auth/me`;

  currentUser$ = new BehaviorSubject<User | null>(null);

  /** Emits when MSAL interaction completes — subscribe to react to sign-in/sign-out events. */
  readonly interactionComplete$: Observable<InteractionStatus>;

  constructor(
    private readonly msal: MsalService,
    private readonly broadcast: MsalBroadcastService,
    private readonly http: HttpClient,
  ) {
    this.interactionComplete$ = this.broadcast.inProgress$.pipe(
      filter(status => status === InteractionStatus.None),
    );
  }

  /**
   * Trigger MSAL redirect sign-in.
   * Navigates the main window to the Entra login page.
   */
  signIn(): Observable<void> {
    return this.msal.loginRedirect({ scopes: [`${clientId}/.default`] }).pipe(map(() => void 0));
  }

  /**
   * Trigger MSAL redirect sign-out.
   */
  signOut(): void {
    const account = this.msal.instance.getActiveAccount()
      ?? this.msal.instance.getAllAccounts()[0];
    this.currentUser$.next(null);
    this.msal.logoutRedirect({ account });
  }

  /**
   * Returns true if there is at least one cached MSAL account.
   */
  get isAuthenticated(): boolean {
    return this.msal.instance.getAllAccounts().length > 0;
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

  /**
   * Fetches the current user from GET /api/auth/me (validates JWT, looks up CosmosDB record).
   * - Returns the User on success (status APPROVED).
   * - Emits null and clears MSAL cache on 401.
   * - Re-throws 403 (PENDING/SUSPENDED) so callers can show the appropriate message.
   */
  loadCurrentUser(): Observable<User | null> {
    return this.http.get<User>(this.meUrl).pipe(
      map(user => {
        this.currentUser$.next(user);
        return user;
      }),
      catchError((err: HttpErrorResponse) => {
        if (err.status === 401) {
          const account = this.msal.instance.getActiveAccount()
            ?? this.msal.instance.getAllAccounts()[0];
          if (account) this.msal.instance.setActiveAccount(null);
          this.currentUser$.next(null);
          return of(null);
        }
        // 202 ACCEPTED means PENDING — treat as a rejection for the UI
        // 403 means PENDING or SUSPENDED — re-throw so callers can route to the right page
        return throwError(() => err);
      }),
    );
  }
}
