import { Injectable, Injector } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  // Injector is used instead of injecting AuthService directly to avoid a
  // circular dependency: AuthService → HttpClient → [interceptors] → AuthService.
  constructor(private injector: Injector) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const auth = this.injector.get(AuthService);
    const token = auth.getToken();   // uses localStorage with in-memory fallback
    if (token) {
      const cloned = req.clone({ setHeaders: { 'X-Session-Token': token } });
      return next.handle(cloned);
    }
    return next.handle(req);
  }
}
