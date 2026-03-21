import { Injectable } from '@angular/core';
import { Subject, Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { CartService, ClaimNotification } from './cart.service';
import { ConfigService } from './config.service';

export interface Toast {
  id: number;
  message: string;
  isError?: boolean;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  toasts: Toast[] = [];
  newClaimEvent$ = new Subject<ClaimNotification>();

  private toastCounter = 0;
  private seenEventKeys = new Set<string>();
  private justActed = new Set<string>();
  private firstPoll = true;
  private pollSub!: Subscription;

  constructor(private cartService: CartService, private configService: ConfigService) {
    this.startPolling();
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
