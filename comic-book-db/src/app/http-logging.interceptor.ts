import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

@Injectable()
export class HttpLoggingInterceptor implements HttpInterceptor {

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const started = Date.now();
    return next.handle(request).pipe(
      tap(
        event => this.logEvent(event, started),
        error => this.logError(error, started)
      )
    );
  }

  private logEvent(event: HttpEvent<any>, started: number) {
    if (event.type === HttpEventType.Response) {
      const elapsed = Date.now() - started;
      const httpResponse = event as HttpResponse<any>;
      console.log(`Request for ${httpResponse.url} took ${elapsed} ms. Status: `, httpResponse.status);
    }
  }

  private logError(error: any, started: number) {
    const elapsed = Date.now() - started;
    console.error(`Request error: ${error.message}. Time: ${elapsed} ms.`);
  }
}
