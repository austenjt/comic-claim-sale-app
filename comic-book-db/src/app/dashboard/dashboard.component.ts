import { Component, OnInit, OnDestroy } from '@angular/core';
import { Comic } from '../comic';
import { ComicService } from '../comic.service';
import { ImageService } from '../image.service';
import { AuthService } from '../auth.service';
import { CartService } from '../cart.service';
import { ToastService } from '../toast.service';
import { UserService } from '../user.service';
import { ConfigService } from '../config.service';
import { Cart } from '../cart';
import { User } from '../user';
import { Observable, of } from 'rxjs';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: [ './dashboard.component.css' ]
})
export class DashboardComponent implements OnInit, OnDestroy {

  comics: Comic[] = [];
  claimedMap: Record<string, string> = {}; // comicId → claimedAt
  myCart: Cart | null = null;
  claimError: string = '';
  loading = true;

  defaultImage: string | null = null;
  testImage: string | null = null;

  awardingComic: Comic | null = null;
  approvedUsers: User[] = [];
  selectedUserId: string | null = null;
  awardLoading = false;
  awardError = '';

  pendingDeleteId: number | null = null;
  deletingId: number | null = null;
  claimingSetId: number | null = null;
  excludeClaimed = false;
  showPricedOnly = false;
  sortOrder = 'date-added';

  // Bidding state: comicId → seconds remaining
  bidCountdowns: Record<string, number> = {};
  private bidTimerInterval: any = null;
  private bidPollInterval: any = null;

  get displayComics(): Comic[] {
    let result = this.comics;
    if (this.excludeClaimed) {
      result = result.filter(c =>
        c.docType === 'SET'
          ? !this.isSetClaimedByOther(c) && !this.isSetInMyCart(c)
          : !this.isClaimedByOther(c.id) && !this.isInMyCart(c.id)
      );
    }
    if (this.showPricedOnly) {
      result = result.filter(c =>
        c.docType === 'SET' ? this.getSetPrice(c) > 0 : c.salePrice != null
      );
    }
    if (this.sortOrder !== 'date-added') {
      result = [...result];
      switch (this.sortOrder) {
        case 'a-z':
          result.sort((a, b) => a.title.localeCompare(b.title));
          break;
        case 'z-a':
          result.sort((a, b) => b.title.localeCompare(a.title));
          break;
        case 'priced-first':
          result.sort((a, b) => {
            const aP = a.docType === 'SET' ? this.getSetPrice(a) : (a.salePrice ?? 0);
            const bP = b.docType === 'SET' ? this.getSetPrice(b) : (b.salePrice ?? 0);
            if (aP > 0 && bP === 0) return -1;
            if (aP === 0 && bP > 0) return 1;
            return 0;
          });
          break;
        case 'claimed-first':
          result.sort((a, b) => {
            const aClaimed = !!this.claimedDate(a.id) ? 1 : 0;
            const bClaimed = !!this.claimedDate(b.id) ? 1 : 0;
            return bClaimed - aClaimed;
          });
          break;
        case 'bidding-first':
          result.sort((a, b) => {
            const aBid = (a.bidOpenedAt || a.bidStartedAt) ? 1 : 0;
            const bBid = (b.bidOpenedAt || b.bidStartedAt) ? 1 : 0;
            return bBid - aBid;
          });
          break;
      }
    }
    return result;
  }


  constructor(
    private comicService: ComicService,
    private imageService: ImageService,
    public auth: AuthService,
    private cartService: CartService,
    private toastService: ToastService,
    private userService: UserService,
    public configService: ConfigService
  ) {}

  ngOnInit(): void {
    this.getRemoteComics();
    this.loadClaimedMap();
    if (this.auth.isApproved()) {
      this.cartService.getMyCart().subscribe({ next: cart => this.myCart = cart, error: () => {} });
    }
    this.toastService.newClaimEvent$.subscribe(n => {
      if (n.eventType === 'RETURN') {
        delete this.claimedMap[n.comicId];
      } else {
        this.claimedMap[n.comicId] = n.claimedAt;
      }
      // Refresh bid state for the affected comic so buttons and countdown stay current
      this.refreshComicBidState(n.comicId);
    });
  }

  ngOnDestroy(): void {
    if (this.bidTimerInterval) clearInterval(this.bidTimerInterval);
    if (this.bidPollInterval) clearInterval(this.bidPollInterval);
  }

  // ─── Bidding helpers ───────────────────────────────────────────────────────

  isBiddingActive(comic: Comic): boolean {
    return !!comic.bidStartedAt && this.bidSecondsRemaining(comic) > 0;
  }

  bidSecondsRemaining(comic: Comic): number {
    if (!comic.bidStartedAt) return 0;
    const endsAt = new Date(comic.bidStartedAt).getTime() +
                   this.configService.biddingCycleMins * 60000;
    return Math.max(0, Math.floor((endsAt - Date.now()) / 1000));
  }

  bidCountdownLabel(comicId: number): string {
    const secs = this.bidCountdowns[String(comicId)] ?? 0;
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  private startBidTimer(): void {
    if (this.bidTimerInterval) return;
    this.bidTimerInterval = setInterval(() => {
      let anyActive = false;
      for (const comic of this.comics) {
        if (comic.bidStartedAt) {
          const secs = this.bidSecondsRemaining(comic);
          this.bidCountdowns[String(comic.id)] = secs;
          if (secs > 0) {
            anyActive = true;
          } else if (secs === 0 && comic.bidStartedAt) {
            // Timer just expired — call finalize
            this.finalizeBidExpiry(comic);
          }
        }
      }
      if (!anyActive) {
        clearInterval(this.bidTimerInterval);
        this.bidTimerInterval = null;
      }
    }, 1000);
    this.startBidPolling();
  }

  private startBidPolling(): void {
    if (this.bidPollInterval) return;
    this.bidPollInterval = setInterval(() => {
      const bidEnabled = this.comics.filter(c => c.enableBid);
      if (bidEnabled.length === 0) {
        clearInterval(this.bidPollInterval);
        this.bidPollInterval = null;
        return;
      }
      for (const comic of bidEnabled) {
        this.refreshComicBidState(String(comic.id));
      }
    }, 5000);
  }

  private finalizeBidExpiry(comic: Comic): void {
    // Clear bidStartedAt locally to prevent repeated calls
    comic.bidStartedAt = null;
    this.cartService.finalizeBid(String(comic.id)).subscribe({
      next: cart => {
        this.myCart = cart;
        this.claimedMap[String(comic.id)] = new Date().toISOString();
        this.toastService.show(`Bidding ended for "${comic.title}" — added to winner's cart.`);
      },
      error: () => {
        // Already finalized or no winner — just refresh claimed map
        this.loadClaimedMap();
      }
    });
  }

  private refreshComicBidState(comicId: string): void {
    const idx = this.comics.findIndex(c => String(c.id) === comicId);
    if (idx < 0 || !this.comics[idx].enableBid) return;
    this.comicService.getComic(this.comics[idx].id).subscribe({
      next: latestComic => {
        if (!latestComic) return;
        const wasActive = this.isBiddingActive(this.comics[idx]);
        const wasOpened = !!this.comics[idx].bidOpenedAt;
        this.comics[idx] = { ...this.comics[idx], ...latestComic };
        // Detect when admin just opened bidding — notify non-admin users
        if (!wasOpened && this.comics[idx].bidOpenedAt && !this.auth.isAdmin()) {
          this.toastService.show(`Bidding is now open for "${this.comics[idx].title}" — place your bid!`);
        }
        if (!wasActive && this.comics[idx].bidStartedAt) {
          this.bidCountdowns[String(this.comics[idx].id)] =
            this.bidSecondsRemaining(this.comics[idx]);
          this.startBidTimer();
        }
      },
      error: () => {}
    });
  }

  cancelBid(comic: Comic): void {
    this.cartService.cancelBid(String(comic.id)).subscribe({
      next: updatedComic => {
        const idx = this.comics.findIndex(c => c.id === comic.id);
        if (idx >= 0) {
          this.comics[idx] = { ...this.comics[idx], ...updatedComic };
        }
        this.toastService.show(`Bidding cancelled for "${comic.title}".`);
      },
      error: err => {
        const msg: string = typeof err?.error === 'string' ? err.error : '';
        this.toastService.show(msg || 'Failed to cancel bidding.', true);
      }
    });
  }

  openBid(comic: Comic): void {
    this.cartService.openBid(String(comic.id)).subscribe({
      next: updatedComic => {
        const idx = this.comics.findIndex(c => c.id === comic.id);
        if (idx >= 0) {
          this.comics[idx] = { ...this.comics[idx], ...updatedComic };
        }
        this.toastService.show(`Bidding opened for "${comic.title}" — waiting for first bid.`);
      },
      error: err => {
        const msg: string = typeof err?.error === 'string' ? err.error : '';
        this.toastService.show(msg || 'Failed to open bidding.', true);
      }
    });
  }

  startBidding(comic: Comic): void {
    this.cartService.startBid(String(comic.id)).subscribe({
      next: updatedComic => {
        // Merge bidding state into the local comic
        const idx = this.comics.findIndex(c => c.id === comic.id);
        if (idx >= 0) {
          this.comics[idx] = { ...this.comics[idx], ...updatedComic };
        }
        this.bidCountdowns[String(comic.id)] = this.bidSecondsRemaining(updatedComic);
        this.startBidTimer();
        this.toastService.show(`Bidding started on "${comic.title}" — ${this.configService.biddingCycleMins} min window open!`);
      },
      error: err => {
        const msg: string = typeof err?.error === 'string' ? err.error : '';
        this.toastService.show(msg || 'Failed to start bidding.');
      }
    });
  }

  placeBid(comic: Comic): void {
    const currentHigh = comic.highBid ?? 0;
    const input = window.prompt(
      `Current high bid: $${currentHigh.toFixed(2)}\n` +
      `Bidder: ${comic.currentBidderName ?? 'none'}\n\n` +
      `Enter your bid amount (must be greater than $${currentHigh.toFixed(2)}):`
    );
    if (input === null) return;
    const amount = parseFloat(input);
    if (isNaN(amount) || amount <= currentHigh) {
      this.toastService.show(`Bid must be greater than $${currentHigh.toFixed(2)}.`, true);
      return;
    }
    this.cartService.placeBid(String(comic.id), amount).subscribe({
      next: updatedComic => {
        const idx = this.comics.findIndex(c => c.id === comic.id);
        if (idx >= 0) {
          this.comics[idx] = { ...this.comics[idx], ...updatedComic };
        }
      },
      error: err => {
        const msg: string = typeof err?.error === 'string' ? err.error : '';
        this.toastService.show(msg || 'Bid failed.');
      }
    });
  }

  getRemoteComics(): void {
    this.loading = true;
    this.comicService.getRemoteNestedComics().subscribe({
      next: comics => {
        if (comics.length === 0) {
          // Remote call returned nothing (likely a cold-start failure swallowed by catchError).
          // Fall back to the in-memory cache so the dashboard still populates.
          this.comicService.getCachedNestedComics().subscribe({
            next: cached => {
              this.comics = cached.filter(c => c.isForSale !== false && !c.dateSold);
              this.loading = false;
              this.initBidCountdowns();
            },
            error: () => { this.loading = false; }
          });
          return;
        }
        this.comics = comics.filter(c => c.isForSale !== false && !c.dateSold);
        this.loading = false;
        this.initBidCountdowns();
      },
      error: () => { this.loading = false; }
    });
  }

  private initBidCountdowns(): void {
    let anyActive = false;
    for (const comic of this.comics) {
      if (comic.bidStartedAt) {
        this.bidCountdowns[String(comic.id)] = this.bidSecondsRemaining(comic);
        if (this.bidSecondsRemaining(comic) > 0) anyActive = true;
      }
    }
    if (anyActive) this.startBidTimer();
    if (this.comics.some(c => c.enableBid)) this.startBidPolling();
  }

  loadClaimedMap(): void {
    this.cartService.getClaimedMap().subscribe({
      next: map => this.claimedMap = map,
      error: () => {}
    });
  }

  private refreshMyCart(): void {
    this.cartService.getMyCart().subscribe({ next: cart => this.myCart = cart, error: () => {} });
  }

  private handleClaimError(err: any): void {
    // Always refresh cart so the UI reflects the real status (hides Claim buttons if cart is locked)
    this.refreshMyCart();
    const msg: string = typeof err?.error === 'string' ? err.error : '';
    if (msg.toLowerCase().includes('not open') || msg.toLowerCase().includes('status:')) {
      this.toastService.show('Your order has already been submitted — new claims are not allowed.');
    } else {
      // Likely beaten to it by another user — refresh claimed map so button updates to "Claimed"
      this.loadClaimedMap();
    }
  }

  comicNumberLabel(comic: Comic): string {
    const n = comic.number;
    if (!n) return '';
    if (n.number != null) return ` #${n.number}`;
    if (n.sentinel) return ` #${n.sentinel}`;
    return '';
  }

  claim(comic: Comic): void {
    this.claimError = '';
    if (comic.docType === 'SET') {
      this.claimSet(comic);
      return;
    }
    this.cartService.addItem(String(comic.id)).subscribe({
      next: cart => {
        this.myCart = cart;
        this.claimedMap[String(comic.id)] = new Date().toISOString();
        this.toastService.markActed(String(comic.id));
        const num = this.comicNumberLabel(comic);
        const price = comic.salePrice != null ? ` — $${comic.salePrice.toFixed(2)}` : '';
        this.toastService.show(`"${comic.title}${num}" added to your cart.${price}`);
      },
      error: (err) => {
        this.handleClaimError(err);
      }
    });
  }

  claimSet(container: Comic): void {
    this.claimingSetId = container.id;
    this.cartService.addSet(String(container.id)).subscribe({
      next: cart => {
        this.myCart = cart;
        const members = container.items ?? [];
        this.claimedMap[String(container.id)] = new Date().toISOString();
        this.toastService.markActed(String(container.id));
        for (const m of members) {
          this.claimedMap[String(m.id)] = new Date().toISOString();
          this.toastService.markActed(String(m.id));
        }
        this.comics = this.comics.filter(c => c.id !== container.id);
        this.claimingSetId = null;
        this.toastService.show(`"${container.title}" set (${members.length} books) added to your cart.`);
      },
      error: (err) => {
        this.claimingSetId = null;
        this.handleClaimError(err);
      }
    });
  }

  getSetPrice(container: Comic): number {
    const members = container.items ?? [];
    return members.reduce((sum, m) => sum + (m.salePrice ?? 0), 0);
  }

  isInMyCart(comicId: number): boolean {
    return this.myCart?.items.some(i => i.comicId === String(comicId)) ?? false;
  }

  isClaimedByOther(comicId: number): boolean {
    return !!this.claimedMap[String(comicId)] && !this.isInMyCart(comicId);
  }

  showClaimButton(comic: Comic): boolean {
    return !!comic.salePrice &&
           !this.isInMyCart(comic.id) &&
           !this.isClaimedByOther(comic.id);
  }

  canClaim(comic: Comic): boolean {
    return this.showClaimButton(comic) &&
           (this.myCart?.status === 'OPEN' || !this.myCart);
  }

  cartLockedTitle(): string {
    const status = this.myCart?.status;
    if (status === 'FINALIZING') return 'Your order has been submitted and is in the review window.';
    if (status === 'FINALIZED') return 'Your order is finalized — wait for fulfillment before claiming again.';
    return '';
  }

  claimedDate(comicId: number): string | null {
    return this.claimedMap[String(comicId)] ?? null;
  }

  openAwardModal(comic: Comic): void {
    this.awardingComic = comic;
    this.selectedUserId = null;
    this.awardError = '';
    if (this.approvedUsers.length === 0) {
      this.userService.getApprovedUsers().subscribe({
        next: users => this.approvedUsers = users.filter(u => !u.isAdmin),
        error: () => this.awardError = 'Failed to load users.'
      });
    }
  }

  closeAwardModal(): void {
    this.awardingComic = null;
    this.selectedUserId = null;
    this.awardError = '';
  }

  isSetInMyCart(container: Comic): boolean {
    const members = container.items ?? [];
    return this.isInMyCart(container.id) || members.some(m => this.isInMyCart(m.id));
  }

  isSetClaimedByOther(container: Comic): boolean {
    const members = container.items ?? [];
    return this.isClaimedByOther(container.id) || members.some(m => this.isClaimedByOther(m.id));
  }

  showClaimSetButton(container: Comic): boolean {
    const members = container.items ?? [];
    return members.length > 0 &&
           !this.isSetInMyCart(container) &&
           !this.isSetClaimedByOther(container);
  }

  canClaimSet(container: Comic): boolean {
    return this.showClaimSetButton(container) &&
           (this.myCart?.status === 'OPEN' || !this.myCart);
  }

  confirmAward(): void {
    if (!this.awardingComic || !this.selectedUserId) return;
    const user = this.approvedUsers.find(u => u.id === this.selectedUserId);
    if (!user) return;
    this.awardLoading = true;
    this.awardError = '';
    const comic = this.awardingComic;
    const label = `"${comic.title}${this.comicNumberLabel(comic)}"`;
    this.cartService.awardComic(String(comic.id), user.id).subscribe({
      next: () => {
        this.claimedMap[String(comic.id)] = new Date().toISOString();
        this.toastService.markActed(String(comic.id));
        this.awardLoading = false;
        this.awardingComic = null;
        this.selectedUserId = null;
        this.toastService.show(`${label} awarded to ${user.name} — FREE!`);
      },
      error: (err) => {
        this.awardError = err?.error || 'Award failed.';
        this.awardLoading = false;
      }
    });
  }

  deleteComic(comic: Comic, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    if (this.pendingDeleteId !== comic.id) {
      this.pendingDeleteId = comic.id;
      return;
    }
    this.pendingDeleteId = null;
    this.deletingId = comic.id;
    this.comicService.deleteComic(comic.id).subscribe({
      next: () => {
        this.comics = this.comics.filter(c => c.id !== comic.id);
        this.comicService.refreshComics();
        this.deletingId = null;
      },
      error: () => { this.deletingId = null; }
    });
  }

  getFullImageURLByName(imageName: string | null | undefined): Observable<string> {
    if (!imageName) {
      return of('assets/comic-book-small.png');
    }
    return of(this.imageService.getRemoteImageURLByName(imageName));
  }

}
