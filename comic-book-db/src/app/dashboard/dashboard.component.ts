import { Component, OnInit, OnDestroy } from '@angular/core';
import { Comic, PagedResponse } from '../comic';
import { ComicService } from '../comic.service';
import { ImageService } from '../image.service';
import { AuthService } from '../auth.service';
import { CartService } from '../cart.service';
import { LogService } from '../log.service';
import { UserService } from '../user.service';
import { ConfigService } from '../config.service';
import { Cart } from '../cart';
import { User } from '../user';
import { Observable, of, Subscription } from 'rxjs';

@Component({
    selector: 'app-dashboard',
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.css'],
    standalone: false
})
export class DashboardComponent implements OnInit, OnDestroy {

  pageItems: Comic[] = [];
  totalCount = 0;
  totalPages = 0;
  currentPage = 1;

  claimedMap: Record<string, string> = {};
  myCart: Cart | null = null;
  claimError: string = '';
  loading = true;

  awardingComic: Comic | null = null;
  approvedUsers: User[] = [];
  selectedUserId: string | null = null;
  awardLoading = false;
  awardError = '';

  pendingDeleteId: number | null = null;
  deletingId: number | null = null;
  claimingSetId: number | null = null;

  // IDs acted on (claimed/awarded) this session — kept in list even when excludeClaimed is on
  private recentlyActedIds = new Set<string>();

  logsExpanded = false;

  readonly Math = Math;
  private static readonly PREFS_KEY = 'dashboard_prefs';

  private _excludeClaimed = false;
  get excludeClaimed() { return this._excludeClaimed; }
  set excludeClaimed(v: boolean) { this._excludeClaimed = v; this.currentPage = 1; this.savePrefs(); }

  private _showPricedOnly = false;
  get showPricedOnly() { return this._showPricedOnly; }
  set showPricedOnly(v: boolean) { this._showPricedOnly = v; this.currentPage = 1; this.loadPage(); this.savePrefs(); }

  private _sortOrder = 'oldest-first';
  get sortOrder() { return this._sortOrder; }
  set sortOrder(v: string) { this._sortOrder = v; this.currentPage = 1; this.loadPage(); this.savePrefs(); }

  private _biddableOnly = false;
  get biddableOnly() { return this._biddableOnly; }
  set biddableOnly(v: boolean) { this._biddableOnly = v; this.currentPage = 1; this.loadPage(); this.savePrefs(); }

  // Bidding state: comicId → seconds remaining
  bidCountdowns: Record<string, number> = {};
  private bidTimerInterval: any = null;
  private bidPollInterval: any = null;

  // Bid modal state
  bidModalComicId: number | null = null;
  bidModalAmount = '';
  bidModalError = '';
  bidModalSubmitting = false;

  get bidModalComic(): Comic | null {
    if (this.bidModalComicId == null) return null;
    return this.pageItems.find(c => c.id === this.bidModalComicId) ?? null;
  }

  private loadSub: Subscription | null = null;
  private loadRetryTimer: any = null;

  get displayComics(): Comic[] {
    let result = this.pageItems;
    if (this.excludeClaimed) {
      result = result.filter(c => {
        if (this.recentlyActedIds.has(String(c.id))) return true;
        return c.docType === 'SET'
          ? !this.isSetClaimedByOther(c) && !this.isSetInMyCart(c)
          : !this.isClaimedByOther(c.id) && !this.isInMyCart(c.id);
      });
    }
    // claimed-first and bidding-first require cross-container data; apply client-side on the current page
    if (this.sortOrder === 'claimed-first') {
      result = [...result].sort((a, b) => {
        const aClaimed = !!this.claimedDate(a.id) ? 1 : 0;
        const bClaimed = !!this.claimedDate(b.id) ? 1 : 0;
        return bClaimed - aClaimed;
      });
    } else if (this.sortOrder === 'bidding-first') {
      result = [...result].sort((a, b) => {
        // Admin sees all enableBid items at top (including not-yet-opened ones with "Start Bid" button).
        // Regular users only see items that admin has already opened or started.
        const isBidRelevant = (c: Comic) => this.auth.isAdmin()
          ? !!c.enableBid && !c.sold
          : !!(c.bidOpenedAt || c.bidStartedAt);
        return (isBidRelevant(b) ? 1 : 0) - (isBidRelevant(a) ? 1 : 0);
      });
    }
    return result;
  }

  constructor(
    private comicService: ComicService,
    private imageService: ImageService,
    public auth: AuthService,
    private cartService: CartService,
    public logService: LogService,
    private userService: UserService,
    public configService: ConfigService
  ) {}

  private savePrefs(): void {
    localStorage.setItem(DashboardComponent.PREFS_KEY, JSON.stringify({
      sortOrder: this._sortOrder,
      currentPage: this.currentPage,
      excludeClaimed: this._excludeClaimed,
      showPricedOnly: this._showPricedOnly,
      biddableOnly: this._biddableOnly
    }));
  }

  private restorePrefs(): void {
    try {
      const raw = localStorage.getItem(DashboardComponent.PREFS_KEY);
      if (!raw) return;
      const prefs = JSON.parse(raw);
      if (prefs.sortOrder) this._sortOrder = prefs.sortOrder;
      if (prefs.currentPage) this.currentPage = prefs.currentPage;
      if (prefs.excludeClaimed != null) this._excludeClaimed = prefs.excludeClaimed;
      if (prefs.showPricedOnly != null) this._showPricedOnly = prefs.showPricedOnly;
      if (prefs.biddableOnly != null) this._biddableOnly = prefs.biddableOnly;
    } catch { /* ignore corrupt data */ }
  }

  ngOnInit(): void {
    this.restorePrefs();
    this.loadPage();
    this.loadClaimedMap();
    if (this.auth.isApproved()) {
      this.cartService.getMyCart().subscribe({ next: cart => this.myCart = cart, error: () => {} });
    }
    this.logService.newClaimEvent$.subscribe(n => {
      if (n.eventType === 'RETURN') {
        delete this.claimedMap[n.comicId];
      } else {
        this.claimedMap[n.comicId] = n.claimedAt;
      }
      this.refreshComicBidState(n.comicId);
    });
  }

  ngOnDestroy(): void {
    if (this.bidTimerInterval) clearInterval(this.bidTimerInterval);
    if (this.bidPollInterval) clearInterval(this.bidPollInterval);
    if (this.loadRetryTimer) clearTimeout(this.loadRetryTimer);
    if (this.loadSub) this.loadSub.unsubscribe();
  }

  loadPage(): void {
    // Cancel any stalled in-flight request and its retry timer before starting fresh
    if (this.loadSub) { this.loadSub.unsubscribe(); this.loadSub = null; }
    if (this.loadRetryTimer) { clearTimeout(this.loadRetryTimer); this.loadRetryTimer = null; }

    this.loading = true;

    this.loadSub = this.comicService.getDashboardPage(
      this.currentPage, this.sortOrder, this.showPricedOnly, this.biddableOnly
    ).subscribe({
      next: (response: PagedResponse<Comic>) => {
        this.loadSub = null;
        if (this.loadRetryTimer) { clearTimeout(this.loadRetryTimer); this.loadRetryTimer = null; }
        this.pageItems = response.items;
        this.totalCount = response.totalCount;
        this.totalPages = response.totalPages;
        this.loading = false;
        this.initBidCountdowns();
      },
      error: () => {
        this.loadSub = null;
        if (this.loadRetryTimer) { clearTimeout(this.loadRetryTimer); this.loadRetryTimer = null; }
        this.loading = false;
      }
    });

    // If still loading after 10 seconds, the request likely stalled — cancel and retry
    this.loadRetryTimer = setTimeout(() => {
      this.loadRetryTimer = null;
      if (this.loading) { this.loadPage(); }
    }, 10000);
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages) {
      this.currentPage++;
      this.savePrefs();
      this.loadPage();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  prevPage(): void {
    if (this.currentPage > 1) {
      this.currentPage--;
      this.savePrefs();
      this.loadPage();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
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
      for (const comic of this.pageItems) {
        if (comic.bidStartedAt) {
          const secs = this.bidSecondsRemaining(comic);
          this.bidCountdowns[String(comic.id)] = secs;
          if (secs > 0) {
            anyActive = true;
          } else if (secs === 0 && comic.bidStartedAt) {
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
      const bidEnabled = this.pageItems.filter(c => c.enableBid && !c.sold);
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
    comic.bidStartedAt = null;
    comic.sold = true;  // prevent bid UI from re-appearing while HTTP round-trip is in flight
    this.cartService.finalizeBid(String(comic.id)).subscribe({
      next: () => {
        this.claimedMap[String(comic.id)] = new Date().toISOString();
        this.logService.logBid(`Bidding ended for "${comic.title}" — added to winner's cart.`);
        if (!this.auth.isAdmin()) this.refreshMyCart();
      },
      error: () => { this.loadClaimedMap(); }
    });
  }

  private refreshComicBidState(comicId: string): void {
    const idx = this.pageItems.findIndex(c => String(c.id) === comicId);
    if (idx < 0 || !this.pageItems[idx].enableBid || this.pageItems[idx].sold) return;
    const wasSold = !!this.pageItems[idx].sold;
    this.comicService.getComic(this.pageItems[idx].id).subscribe({
      next: latestComic => {
        if (!latestComic) return;
        const wasActive = this.isBiddingActive(this.pageItems[idx]);
        const wasOpened = !!this.pageItems[idx].bidOpenedAt;
        this.pageItems[idx] = { ...this.pageItems[idx], ...latestComic };
        if (!wasOpened && this.pageItems[idx].bidOpenedAt && !this.auth.isAdmin()) {
          this.logService.logBid(`Bidding is now open for "${this.pageItems[idx].title}" — place your bid!`);
        }
        if (!wasActive && this.pageItems[idx].bidStartedAt) {
          this.bidCountdowns[String(this.pageItems[idx].id)] =
            this.bidSecondsRemaining(this.pageItems[idx]);
          this.startBidTimer();
        }
        // sold=true is only set after full DB commit — reliable signal to refresh claim state
        if (!wasSold && this.pageItems[idx].sold) {
          this.loadClaimedMap();
          if (!this.auth.isAdmin()) this.refreshMyCart();
        }
      },
      error: () => {}
    });
  }

  cancelBid(comic: Comic): void {
    this.cartService.cancelBid(String(comic.id)).subscribe({
      next: updatedComic => {
        const idx = this.pageItems.findIndex(c => c.id === comic.id);
        if (idx >= 0) this.pageItems[idx] = { ...this.pageItems[idx], ...updatedComic };
        this.logService.logBid(`Bidding cancelled for "${comic.title}".`);
      },
      error: err => {
        const msg: string = typeof err?.error === 'string' ? err.error : '';
        this.logService.log(msg || 'Failed to cancel bidding.', true);
      }
    });
  }

  openBid(comic: Comic): void {
    this.cartService.openBid(String(comic.id)).subscribe({
      next: updatedComic => {
        const idx = this.pageItems.findIndex(c => c.id === comic.id);
        if (idx >= 0) this.pageItems[idx] = { ...this.pageItems[idx], ...updatedComic };
        this.logService.logBid(`Bidding opened for "${comic.title}" — waiting for first bid.`);
      },
      error: err => {
        const msg: string = typeof err?.error === 'string' ? err.error : '';
        this.logService.log(msg || 'Failed to open bidding.', true);
      }
    });
  }

  startBidding(comic: Comic): void {
    this.cartService.startBid(String(comic.id)).subscribe({
      next: updatedComic => {
        const idx = this.pageItems.findIndex(c => c.id === comic.id);
        if (idx >= 0) this.pageItems[idx] = { ...this.pageItems[idx], ...updatedComic };
        this.bidCountdowns[String(comic.id)] = this.bidSecondsRemaining(updatedComic);
        this.startBidTimer();
        this.logService.logBid(`Bidding started on "${comic.title}" — ${this.configService.biddingCycleMins} minute window open!`);
      },
      error: err => {
        const msg: string = typeof err?.error === 'string' ? err.error : '';
        this.logService.log(msg || 'Failed to start bidding.');
      }
    });
  }

  placeBid(comic: Comic): void {
    this.bidModalComicId = comic.id;
    this.bidModalAmount = '';
    this.bidModalError = '';
    this.bidModalSubmitting = false;
  }

  closeBidModal(): void {
    this.bidModalComicId = null;
    this.bidModalAmount = '';
    this.bidModalError = '';
    this.bidModalSubmitting = false;
  }

  minNextBid(highBid: number, startPrice?: number | null): number {
    if (highBid <= 0) {
      return Math.max(startPrice ?? 1.00, 1.00);
    }
    const nextQuarter = Math.ceil((highBid + 0.001) / 0.25) * 0.25;
    return Math.max(nextQuarter, 1.00);
  }

  get bidModalWarning(): string {
    const comic = this.bidModalComic;
    if (!comic) return '';
    const amount = parseFloat(this.bidModalAmount);
    const currentHigh = comic.highBid ?? 0;
    if (!isNaN(amount) && amount >= currentHigh + 10) {
      return `Your bid is $${(amount - currentHigh).toFixed(2)} over the current high bid — double-check before submitting.`;
    }
    return '';
  }

  submitBid(): void {
    const comic = this.bidModalComic;
    if (!comic) return;
    const amount = parseFloat(this.bidModalAmount);
    const currentHigh = comic.highBid ?? 0;
    if (isNaN(amount) || amount <= currentHigh) {
      this.bidModalError = `Amount must exceed the current high bid of $${currentHigh.toFixed(2)}.`;
      return;
    }
    if (Math.abs(Math.round(amount * 4) - amount * 4) > 0.001) {
      this.bidModalError = 'Bid must be in whole dollar or $0.25 increments (e.g. $1.00, $1.25, $1.50).';
      return;
    }
    this.bidModalSubmitting = true;
    this.bidModalError = '';
    this.cartService.placeBid(String(comic.id), amount).subscribe({
      next: updatedComic => {
        const idx = this.pageItems.findIndex(c => c.id === comic.id);
        if (idx >= 0) this.pageItems[idx] = { ...this.pageItems[idx], ...updatedComic };
        this.closeBidModal();
      },
      error: err => {
        const msg: string = typeof err?.error === 'string' ? err.error : '';
        this.bidModalError = msg || 'Bid failed.';
        this.bidModalSubmitting = false;
        this.refreshComicBidState(String(comic.id));
      }
    });
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
    this.refreshMyCart();
    const msg: string = typeof err?.error === 'string' ? err.error : '';
    if (msg.toLowerCase().includes('not open') || msg.toLowerCase().includes('status:')) {
      this.logService.log('Your order has already been submitted — new claims are not allowed.');
    } else {
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
        this.recentlyActedIds.add(String(comic.id));
        this.logService.markActed(String(comic.id));
        const num = this.comicNumberLabel(comic);
        const price = comic.salePrice != null ? ` — $${comic.salePrice.toFixed(2)}` : '';
        const claimName = this.auth.currentUser$.value?.name ?? 'User';
        this.logService.log(`"${comic.title}${num}" added to ${claimName}'s cart.${price}`);
      },
      error: (err) => { this.handleClaimError(err); }
    });
  }

  claimSet(container: Comic): void {
    this.claimingSetId = container.id;
    this.cartService.addSet(String(container.id)).subscribe({
      next: cart => {
        this.myCart = cart;
        const members = container.items ?? [];
        this.claimedMap[String(container.id)] = new Date().toISOString();
        this.recentlyActedIds.add(String(container.id));
        this.logService.markActed(String(container.id));
        for (const m of members) {
          this.claimedMap[String(m.id)] = new Date().toISOString();
          this.recentlyActedIds.add(String(m.id));
          this.logService.markActed(String(m.id));
        }
        this.claimingSetId = null;
        const setClaimName = this.auth.currentUser$.value?.name ?? 'User';
        this.logService.log(`"${container.title}" set (${members.length} books) added to ${setClaimName}'s cart.`);
        this.loadPage();
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

  canBid(): boolean {
    return this.myCart?.status === 'OPEN' || !this.myCart;
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
        this.recentlyActedIds.add(String(comic.id));
        this.logService.markActed(String(comic.id));
        this.awardLoading = false;
        this.awardingComic = null;
        this.selectedUserId = null;
        this.logService.log(`${label} awarded to ${user.name} — FREE!`);
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
        this.deletingId = null;
        this.loadPage();
      },
      error: () => { this.deletingId = null; }
    });
  }

  private initBidCountdowns(): void {
    let anyActive = false;
    for (const comic of this.pageItems) {
      if (comic.bidStartedAt) {
        this.bidCountdowns[String(comic.id)] = this.bidSecondsRemaining(comic);
        if (this.bidSecondsRemaining(comic) > 0) anyActive = true;
      }
    }
    if (anyActive) this.startBidTimer();
    if (this.pageItems.some(c => c.enableBid && !c.sold)) this.startBidPolling();
  }

  trackById(_index: number, comic: Comic): number { return comic.id; }

  getFullImageURLByName(imageName: string | null | undefined): Observable<string> {
    if (!imageName) return of('assets/comic-book-small.png');
    return of(this.imageService.getRemoteImageURLByName(imageName));
  }
}
