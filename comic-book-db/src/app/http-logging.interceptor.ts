import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

@Injectable()
export class HttpLoggingInterceptor implements HttpInterceptor {

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const started = Date.now();
    return next.handle(request).pipe(
      tap({
        error: error => {
          const elapsed = Date.now() - started;
          console.error(`Request error: ${error.message}. Time: ${elapsed} ms.`);
        }
      })
    );
  }
}
