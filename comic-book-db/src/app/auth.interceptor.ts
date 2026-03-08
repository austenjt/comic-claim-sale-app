import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';

const SESSION_KEY = 'session_token';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = localStorage.getItem(SESSION_KEY);
    if (token) {
      const cloned = req.clone({ setHeaders: { 'X-Session-Token': token } });
      return next.handle(cloned);
    }
    return next.handle(req);
  }
}
