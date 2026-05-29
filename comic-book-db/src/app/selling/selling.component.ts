import { Component, DestroyRef, HostListener, OnInit, OnDestroy, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title, Meta } from '@angular/platform-browser';
import { Comic, PagedResponse } from '../comic';
import { ComicService } from '../comic.service';
import { AuthService } from '../auth.service';
import { CartService } from '../cart.service';
import { LogService } from '../log.service';
import { UserService } from '../user.service';
import { ConfigService } from '../config.service';
import { DashboardNavService } from '../dashboard-nav.service';
import { Cart } from '../cart';
import { User } from '../user';
import { Subscription } from 'rxjs';
import { DocType } from '../comic.enums';

@Component({
    selector: 'app-selling',
    templateUrl: './selling.component.html',
    styleUrls: ['./selling.component.css'],
    standalone: false
})
export class SellingComponent implements OnInit, OnDestroy {

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
  copyingId: number | null = null;
  copiedId: number | null = null;

  // IDs acted on (claimed/awarded) this session — kept in list even when excludeClaimed is on
  private recentlyActedIds = new Set<string>();


  readonly Math = Math;
  private static readonly PREFS_KEY = 'dashboard_prefs';

  private _excludeClaimed = false;
  get excludeClaimed() { return this._excludeClaimed; }
  set excludeClaimed(v: boolean) { this._excludeClaimed = v; this.currentPage = 1; this.savePrefs(); }

  private _sortOrder = 'oldest-first';
  get sortOrder() { return this._sortOrder; }
  set sortOrder(v: string) { this._sortOrder = v; this.currentPage = 1; this.loadPage(); this.savePrefs(); }

  private loadSub: Subscription | null = null;
  private loadRetryTimer: any = null;
  private destroyRef = inject(DestroyRef);

  get displayComics(): Comic[] {
    let result = this.pageItems;
    if (this.excludeClaimed) {
      result = result.filter(c => {
        if (this.recentlyActedIds.has(String(c.id))) return true;
        return c.docType === DocType.SET
          ? !this.isSetClaimedByOther(c) && !this.isSetInMyCart(c)
          : !this.isClaimedByOther(c.id) && !this.isInMyCart(c.id);
      });
    }
    // claimed-first requires cross-container data; apply client-side on the current page
    if (this.sortOrder === 'claimed-first') {
      result = [...result].sort((a, b) => {
        const aClaimed = !!this.claimedDate(a.id) ? 1 : 0;
        const bClaimed = !!this.claimedDate(b.id) ? 1 : 0;
        return bClaimed - aClaimed;
      });
    }
    return result;
  }

  constructor(
    private comicService: ComicService,
    public auth: AuthService,
    private cartService: CartService,
    private logService: LogService,
    private userService: UserService,
    public configService: ConfigService,
    private navService: DashboardNavService,
    private title: Title,
    private meta: Meta,
  ) {}

  private savePrefs(): void {
    localStorage.setItem(SellingComponent.PREFS_KEY, JSON.stringify({
      sortOrder: this._sortOrder,
      currentPage: this.currentPage,
      excludeClaimed: this._excludeClaimed
    }));
  }

  private restorePrefs(): void {
    try {
      const raw = localStorage.getItem(SellingComponent.PREFS_KEY);
      if (!raw) return;
      const prefs = JSON.parse(raw);
      if (prefs.sortOrder) this._sortOrder = prefs.sortOrder;
      if (prefs.currentPage) this.currentPage = prefs.currentPage;
      if (prefs.excludeClaimed != null) this._excludeClaimed = prefs.excludeClaimed;
    } catch { /* ignore corrupt data */ }
  }

  ngOnInit(): void {
    this.title.setTitle('Comics for Sale — Lightning Comics PDX');
    this.meta.updateTag({ name: 'description', content: 'Browse graded and raw comics available for claim. CGC, CBCS, and raw books from Golden Age to Modern. Oregon City, OR.' });
    this.restorePrefs();
    this.loadPage();
    this.loadClaimedMap();
    if (this.auth.isApproved()) {
      this.cartService.getMyCart()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ next: cart => this.myCart = cart, error: () => {} });
    }
    this.logService.newClaimEvent$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(n => {
        if (n.eventType === 'RETURN') {
          delete this.claimedMap[n.comicId];
        } else {
          this.claimedMap[n.comicId] = n.claimedAt;
        }
      });
  }

  ngOnDestroy(): void {
    if (this.loadRetryTimer) clearTimeout(this.loadRetryTimer);
    if (this.loadSub) this.loadSub.unsubscribe();
  }

  loadPage(): void {
    // Cancel any stalled in-flight request and its retry timer before starting fresh
    if (this.loadSub) { this.loadSub.unsubscribe(); this.loadSub = null; }
    if (this.loadRetryTimer) { clearTimeout(this.loadRetryTimer); this.loadRetryTimer = null; }

    this.loading = true;

    this.loadSub = this.comicService.getDashboardPage(
      this.currentPage, this.sortOrder
    ).subscribe({
      next: (response: PagedResponse<Comic>) => {
        this.loadSub = null;
        if (this.loadRetryTimer) { clearTimeout(this.loadRetryTimer); this.loadRetryTimer = null; }
        this.pageItems = response.items;
        this.totalCount = response.totalCount;
        this.totalPages = response.totalPages;
        this.loading = false;
        this.navService.setList(this.pageItems);
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

  goToPage(page: number): void {
    if (page < 1 || page > this.totalPages || page === this.currentPage) return;
    this.currentPage = page;
    this.savePrefs();
    this.loadPage();
    window.scrollTo({ top: 0, behavior: 'smooth' });
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
    this.loadClaimedMap();
  }

  claim(comic: Comic): void {
    this.claimError = '';
    if (comic.docType === DocType.SET) {
      this.claimSet(comic);
      return;
    }
    this.cartService.addItem(String(comic.id)).subscribe({
      next: cart => {
        this.myCart = cart;
        this.claimedMap[String(comic.id)] = new Date().toISOString();
        this.recentlyActedIds.add(String(comic.id));
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
        for (const m of members) {
          this.claimedMap[String(m.id)] = new Date().toISOString();
          this.recentlyActedIds.add(String(m.id));
        }
        this.claimingSetId = null;
        this.loadPage();
      },
      error: (err) => {
        this.claimingSetId = null;
        this.handleClaimError(err);
      }
    });
  }

  isInMyCart(comicId: number): boolean {
    return this.myCart?.items.some(i => i.comicId === String(comicId)) ?? false;
  }

  isClaimedByOther(comicId: number): boolean {
    return !!this.claimedMap[String(comicId)] && !this.isInMyCart(comicId);
  }

  showClaimButton(comic: Comic): boolean {
    return !!comic.isForSale &&
           !!comic.salePrice &&
           !this.isInMyCart(comic.id) &&
           !this.isClaimedByOther(comic.id);
  }

  canClaim(comic: Comic): boolean {
    return this.showClaimButton(comic) &&
           (this.myCart?.status === 'OPEN' || !this.myCart);
  }

  cartLockedTitle(): string {
    const status = this.myCart?.status;
    if (status === 'SUBMITTED') return 'Your order has been submitted — wait for fulfillment before claiming again.';
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

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.awardingComic) this.closeAwardModal();
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
    return !!container.isForSale &&
           members.length > 0 &&
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
    this.cartService.awardComic(String(comic.id), user.id).subscribe({
      next: () => {
        this.claimedMap[String(comic.id)] = new Date().toISOString();
        this.recentlyActedIds.add(String(comic.id));
        this.awardLoading = false;
        this.awardingComic = null;
        this.selectedUserId = null;
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

  copyComic(comic: Comic): void {
    this.copyingId = comic.id;
    const copy: Comic = {
      ...comic,
      id: -1,
      title: comic.title + ' (Copied)',
      dateSold: null,
      soldTo: null,
      sold: null,
      items: undefined,
      smallCachedImageId: null,
      largeCachedImageId: null,
      smallBackImageId: null,
      largeBackImageId: null,
      comicCondition: null,
      salePrice: null,
      viewCount: null,
      ebayListingUrl: null,
    };
    this.comicService.addComic(copy).subscribe({
      next: () => {
        this.copyingId = null;
        this.copiedId = comic.id;
        setTimeout(() => { this.copiedId = null; this.loadPage(); }, 2000);
      },
      error: () => { this.copyingId = null; }
    });
  }

  markForSale(comic: Comic): void {
    const updated: Comic = { ...comic, isForSale: true };
    this.comicService.updateComic(updated).subscribe({
      next: () => {
        const idx = this.pageItems.findIndex(c => c.id === comic.id);
        if (idx >= 0) this.pageItems[idx] = { ...this.pageItems[idx], isForSale: true };
      },
      error: () => {}
    });
  }

  trackById(_index: number, comic: Comic): number { return comic.id; }
}
