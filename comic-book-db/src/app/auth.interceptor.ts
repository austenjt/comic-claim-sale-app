import { Injectable, Injector } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MsalInterceptor } from '@azure/msal-angular';

/**
 * Delegates to MsalInterceptor to attach Bearer tokens to requests
 * targeting the protected API (configured via msalInterceptorConfig in app.module.ts).
 *
 * Using Injector to avoid circular dependency issues with MsalInterceptor's own deps.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private injector: Injector) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const msalInterceptor = this.injector.get(MsalInterceptor);
    return msalInterceptor.intercept(req, next);
  }
}
