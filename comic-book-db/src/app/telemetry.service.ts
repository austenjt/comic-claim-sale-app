import { ErrorHandler, Injectable, NgZone } from '@angular/core';

declare global {
  interface Window {
    gtag?: (...args: any[]) => void;
  }
}

/**
 * Lightweight telemetry: console + GA4 exception events. Use `report(err, source)`
 * to log a caught error from anywhere; the global ErrorHandler also routes uncaught
 * errors here so the same pipeline gets used for both.
 */
@Injectable({ providedIn: 'root' })
export class TelemetryService {
  report(error: unknown, source?: string): void {
    const message = this.describe(error);
    const ctx = source ? `[${source}] ${message}` : message;
    console.error(ctx, error);
    if (typeof window !== 'undefined' && typeof window.gtag === 'function') {
      try {
        window.gtag('event', 'exception', {
          description: ctx.slice(0, 500),
          fatal: false,
          source: source ?? 'unknown',
        });
      } catch {
        // gtag failures must never break the app
      }
    }
  }

  private describe(error: unknown): string {
    if (!error) return 'Unknown error';
    if (error instanceof Error) return error.message || error.name;
    if (typeof error === 'string') return error;
    try { return JSON.stringify(error); } catch { return String(error); }
  }
}

@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  constructor(private telemetry: TelemetryService, private zone: NgZone) {}

  handleError(error: unknown): void {
    // Run outside Angular's zone so the report itself can't re-trigger CD storms
    this.zone.runOutsideAngular(() => this.telemetry.report(error, 'GlobalErrorHandler'));
  }
}
