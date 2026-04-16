import { Injectable, OnDestroy } from '@angular/core';
import { fromEvent, merge, Observable, of, timer } from 'rxjs';
import { distinctUntilChanged, map, shareReplay, startWith, switchMap } from 'rxjs/operators';

const IDLE_TIMEOUT_MS = 15 * 60 * 1000;

@Injectable({ providedIn: 'root' })
export class InactivityService implements OnDestroy {

  readonly isIdle$: Observable<boolean>;

  private readonly activityEvents$ = merge(
    fromEvent(document, 'mousemove'),
    fromEvent(document, 'click'),
    fromEvent(document, 'keydown'),
    fromEvent(document, 'scroll', { passive: true }),
    fromEvent(document, 'touchstart', { passive: true }),
  );

  constructor() {
    this.isIdle$ = this.activityEvents$.pipe(
      switchMap(() => merge(
        of(false),
        timer(IDLE_TIMEOUT_MS).pipe(map(() => true))
      )),
      startWith(false),
      distinctUntilChanged(),
      shareReplay(1),
    );
  }

  ngOnDestroy(): void {}
}
