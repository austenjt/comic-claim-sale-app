import { Injectable } from '@angular/core';
import { Subject, Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { CartService, ClaimNotification } from './cart.service';
import { InactivityService } from './inactivity.service';

@Injectable({ providedIn: 'root' })
export class LogService {
  newClaimEvent$ = new Subject<ClaimNotification>();

  private seenEventKeys = new Set<string>();
  private firstPoll = true;
  private pollSub!: Subscription;

  constructor(
    private cartService: CartService,
    private inactivityService: InactivityService,
  ) {
    this.inactivityService.isIdle$.subscribe(idle => {
      if (idle) {
        this.pollSub?.unsubscribe();
      } else {
        this.startPolling();
      }
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
          }
        }
        this.firstPoll = false;
      },
      error: () => {}
    });
  }
}
