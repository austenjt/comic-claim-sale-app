import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject, Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { CartService, ClaimNotification } from './cart.service';
import { ConfigService } from './config.service';

export interface Toast {
  id: number;
  message: string;
  isError?: boolean;
}

export interface LogEntry {
  message: string;
  timestamp: Date;
  isError: boolean;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  toasts: Toast[] = [];
  logEntries: LogEntry[] = [];
  newClaimEvent$ = new Subject<ClaimNotification>();

  private readonly activityLogsUrl = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api/activity-logs';
  private toastCounter = 0;
  private seenEventKeys = new Set<string>();
  private justActed = new Set<string>();
  private firstPoll = true;
  private pollSub!: Subscription;

  constructor(
    private cartService: CartService,
    private configService: ConfigService,
    private http: HttpClient
  ) {
    this.startPolling();
    this.loadPersistedLogs();
  }

  private loadPersistedLogs(): void {
    this.http.get<any[]>(this.activityLogsUrl).subscribe({
      next: entries => {
        const loaded: LogEntry[] = entries.map(e => ({
          message: e.message,
          timestamp: new Date(e.timestamp),
          isError: e.isError ?? false
        }));
        // Prepend any in-session entries already collected before the HTTP response arrived
        this.logEntries = [...this.logEntries, ...loaded].slice(0, 100);
      },
      error: () => {}
    });
  }

  private startPolling(): void {
    this.pollSub = timer(0, 4000).pipe(
      switchMap(() => this.cartService.getNotifications())
    ).subscribe({
      next: notifications => {
        for (const n of notifications) {
          const key = `${n.comicId}:${n.claimedAt}`;
          if (this.firstPoll) {
            this.seenEventKeys.add(key);
          } else if (!this.seenEventKeys.has(key)) {
            this.seenEventKeys.add(key);
            this.newClaimEvent$.next(n);
            const numPart = n.comicNumber ? ` ${n.comicNumber}` : '';
            if (n.eventType === 'RETURN') {
              this.show(`"${n.comicTitle}${numPart}" Returned to sale`);
            } else if (!this.justActed.has(n.comicId)) {
              if (n.price === 0) {
                this.show(`"${n.comicTitle}${numPart}" awarded to ${n.userName} — FREE!`);
              } else {
                this.show(`"${n.comicTitle}${numPart}" added to ${n.userName}'s cart — $${n.price.toFixed(2)}`);
              }
            }
          }
        }
        this.firstPoll = false;
      },
      error: () => {}
    });
  }

  show(message: string, isError = false): void {
    if (this.configService.pauseNotifications) return;
    const id = ++this.toastCounter;
    this.toasts.push({ id, message, isError });
    setTimeout(() => this.dismiss(id), 30000);
    const entry: LogEntry = { message, timestamp: new Date(), isError };
    this.logEntries.unshift(entry);
    if (this.logEntries.length > 100) this.logEntries.length = 100;
    this.http.post(this.activityLogsUrl, { message, timestamp: entry.timestamp.toISOString(), isError })
      .subscribe({ error: () => {} });
  }

  dismiss(id: number): void {
    this.toasts = this.toasts.filter(t => t.id !== id);
  }

  clearAll(): void {
    this.toasts = [];
  }

  markActed(comicId: string): void {
    this.justActed.add(comicId);
    setTimeout(() => this.justActed.delete(comicId), 15000);
  }
}
