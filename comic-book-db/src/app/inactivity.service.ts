import { Injectable, OnDestroy } from '@angular/core';
import { fromEvent, merge, Observable } from 'rxjs';
import { debounceTime, distinctUntilChanged, map, shareReplay, startWith } from 'rxjs/operators';

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
      debounceTime(IDLE_TIMEOUT_MS),
      map(() => true),
      startWith(false),
      distinctUntilChanged(),
      shareReplay(1),
    );
  }

  ngOnDestroy(): void {}
}
