import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Observable, from } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { MsalService } from '@azure/msal-angular';
import { apiBase, clientId } from './auth.config';

/**
 * Attaches a Bearer token to requests targeting the Lightning Comics API,
 * but only when an MSAL account is already cached.
 *
 * Unlike MsalInterceptor with InteractionType.Redirect, this interceptor
 * never triggers an automatic redirect to the login page — it simply passes
 * the request through unauthenticated when no account is in cache.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private msal: MsalService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // Only add tokens to requests targeting the Lightning Comics API
    if (!req.url.startsWith(apiBase)) {
      return next.handle(req);
    }

    // No cached account → pass through without a token; do NOT redirect to login
    const accounts = this.msal.instance.getAllAccounts();
    if (accounts.length === 0) {
      return next.handle(req);
    }

    const account = this.msal.instance.getActiveAccount() ?? accounts[0];

    return from(
      this.msal.instance.acquireTokenSilent({ scopes: [`${clientId}/.default`], account })
    ).pipe(
      switchMap(result =>
        next.handle(req.clone({ setHeaders: { Authorization: `Bearer ${result.accessToken}` } }))
      ),
      catchError(() => next.handle(req)),
    );
  }
}
