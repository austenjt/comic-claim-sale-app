import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';

import { Comic } from '../comic';
import { Cart } from '../cart';
import { ComicService } from '../comic.service';
import { ImageService } from '../image.service';
import { CartService } from '../cart.service';
import { ToastService } from '../toast.service';
import { AuthService } from '../auth.service';
import { ConfigService } from '../config.service';
import { Observable, of, map, Subscription } from 'rxjs';

@Component({
  selector: 'app-comic-detail',
  templateUrl: './comic-detail.component.html',
  styleUrls: [ './comic-detail.component.css' ]
})
export class ComicDetailComponent implements OnInit, OnDestroy {

  comic: Comic | undefined;
  activeImage: 'front' | 'back' = 'front';
  zoomOpen = false;
  claimedMap: Record<string, string> = {};
  myCart: Cart | null = null;
  claimError = '';
  loading = true;
  actionLoading = false;
  bidSecondsRemaining = 0;
  private bidTimerInterval: any = null;
  private bidPollInterval: any = null;
  private claimEventSub: Subscription | null = null;
  imageUploading = false;
  imageUploadErrorSummary = '';
  imageUploadErrorDetail = '';
  imageUploadErrorExpanded = false;
  backImageUploading = false;
  backImageUploadErrorSummary = '';
  backImageUploadErrorDetail = '';
  backImageUploadErrorExpanded = false;
  linkCopied = false;

  constructor(
    private route: ActivatedRoute,
    private comicService: ComicService,
    private imageService: ImageService,
    private cartService: CartService,
    private toastService: ToastService,
    public auth: AuthService,
    public configService: ConfigService,
    private location: Location
  ) {}

  get activeLargeImageId(): string | null | undefined {
    return this.activeImage === 'front' ? this.comic?.largeCachedImageId : this.comic?.largeBackImageId;
  }

  selectImage(which: 'front' | 'back'): void {
    this.activeImage = which;
  }

  get comicHeading(): string {
    if (!this.comic) return '';
    const n = this.comic.number;
    if (!n) return this.comic.title;
    if (n.number != null) return `${this.comic.title} #${n.number}`;
    if (n.sentinel) return `${this.comic.title} #${n.sentinel}`;
    return this.comic.title;
  }

  ngOnInit(): void {
    this.getComic();
    this.cartService.getClaimedMap().subscribe({ next: m => this.claimedMap = m, error: () => {} });
    if (this.auth.isApproved() && !this.auth.isAdmin()) {
      this.cartService.getMyCart().subscribe({ next: cart => this.myCart = cart, error: () => {} });
    }
  }

  claimedDate(comicId: number): string | null {
    return this.claimedMap[String(comicId)] ?? null;
  }

  isInMyCart(comicId: number): boolean {
    return this.myCart?.items.some(i => i.comicId === String(comicId)) ?? false;
  }

  isWonViaBid(comicId: number): boolean {
    return this.myCart?.items.some(i => i.comicId === String(comicId) && i.wonViaBid) ?? false;
  }

  canClaim(comicId: number): boolean {
    return !this.claimedDate(comicId) &&
           !this.comic?.soldTo &&
           !!this.comic?.salePrice &&
           (this.myCart?.status === 'OPEN' || !this.myCart);
  }

  claim(): void {
    if (!this.comic) return;
    this.claimError = '';
    this.actionLoading = true;
    this.cartService.addItem(String(this.comic.id)).subscribe({
      next: cart => {
        this.myCart = cart;
        this.claimedMap[String(this.comic!.id)] = new Date().toISOString();
        this.actionLoading = false;
      },
      error: err => {
        this.claimError = err?.error || 'Failed to claim comic.';
        this.actionLoading = false;
      }
    });
  }

  release(): void {
    if (!this.comic) return;
    this.claimError = '';
    this.actionLoading = true;
    const heading = this.comicHeading;
    this.cartService.removeItem(String(this.comic.id)).subscribe({
      next: cart => {
        this.myCart = cart;
        delete this.claimedMap[String(this.comic!.id)];
        this.actionLoading = false;
        this.toastService.show(`"${heading}" Returned to sale`);
      },
      error: err => {
        this.claimError = err?.error || 'Failed to release comic.';
        this.actionLoading = false;
      }
    });
  }

  get bidHistoryDesc() {
    return [...(this.comic?.bidHistory ?? [])].reverse();
  }

  ngOnDestroy(): void {
    if (this.bidTimerInterval) clearInterval(this.bidTimerInterval);
    if (this.bidPollInterval) clearInterval(this.bidPollInterval);
    if (this.claimEventSub) this.claimEventSub.unsubscribe();
  }

  isBiddingActive(): boolean {
    if (!this.comic?.bidStartedAt) return false;
    return this.bidSecondsRemaining > 0;
  }

  get bidCountdownLabel(): string {
    const m = Math.floor(this.bidSecondsRemaining / 60);
    const s = this.bidSecondsRemaining % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  private startBidTimer(): void {
    if (this.bidTimerInterval) return;
    this.bidTimerInterval = setInterval(() => {
      if (!this.comic?.bidStartedAt) {
        clearInterval(this.bidTimerInterval);
        this.bidTimerInterval = null;
        return;
      }
      const endsAt = new Date(this.comic.bidStartedAt).getTime() +
                     this.configService.biddingCycleMins * 60000;
      this.bidSecondsRemaining = Math.max(0, Math.floor((endsAt - Date.now()) / 1000));
      if (this.bidSecondsRemaining === 0) {
        clearInterval(this.bidTimerInterval);
        this.bidTimerInterval = null;
        this.onBidExpired();
      }
    }, 1000);
  }

  private onBidExpired(): void {
    if (!this.comic) return;
    const comicId = String(this.comic.id);
    this.comic.bidStartedAt = null;
    this.cartService.finalizeBid(comicId).subscribe({
      next: cart => {
        this.myCart = cart;
        this.claimedMap[comicId] = new Date().toISOString();
        this.toastService.show('Bidding ended — comic added to winner\'s cart.');
      },
      error: () => { this.loadClaimedMap(); }
    });
  }

  private loadClaimedMap(): void {
    this.cartService.getClaimedMap().subscribe({ next: m => this.claimedMap = m, error: () => {} });
  }

  startBidding(): void {
    if (!this.comic) return;
    this.actionLoading = true;
    this.cartService.startBid(String(this.comic.id)).subscribe({
      next: updatedComic => {
        this.comic = { ...this.comic!, ...updatedComic };
        const endsAt = new Date(this.comic.bidStartedAt!).getTime() +
                       this.configService.biddingCycleMins * 60000;
        this.bidSecondsRemaining = Math.max(0, Math.floor((endsAt - Date.now()) / 1000));
        this.startBidTimer();
        this.actionLoading = false;
        this.toastService.show(`Bidding started — ${this.configService.biddingCycleMins} min window open!`);
        // Ensure all viewers start getting live updates from this point on
        this.setupBidRefresh();
      },
      error: err => {
        this.claimError = err?.error || 'Failed to start bidding.';
        this.actionLoading = false;
      }
    });
  }

  placeBid(): void {
    if (!this.comic) return;
    const currentHigh = this.comic.highBid ?? 0;
    const input = window.prompt(
      `Current high bid: $${currentHigh.toFixed(2)}\n` +
      `Bidder: ${this.comic.currentBidderName ?? 'none'}\n\n` +
      `Enter your bid (must be greater than $${currentHigh.toFixed(2)}):`
    );
    if (input === null) return;
    const amount = parseFloat(input);
    if (isNaN(amount) || amount <= currentHigh) {
      this.claimError = `Bid must be greater than $${currentHigh.toFixed(2)}.`;
      return;
    }
    this.actionLoading = true;
    this.cartService.placeBid(String(this.comic.id), amount).subscribe({
      next: updatedComic => {
        this.comic = { ...this.comic!, ...updatedComic };
        // bidStartedAt was reset on the server — restart the timer if it had stopped
        if (this.comic.bidStartedAt && !this.bidTimerInterval) {
          const endsAt = new Date(this.comic.bidStartedAt).getTime() +
                         this.configService.biddingCycleMins * 60000;
          this.bidSecondsRemaining = Math.max(0, Math.floor((endsAt - Date.now()) / 1000));
          if (this.bidSecondsRemaining > 0) this.startBidTimer();
        }
        // If the timer is still running it will self-correct on the next 1s tick
        this.actionLoading = false;
        this.claimError = '';
        this.toastService.show(`Bid of $${amount.toFixed(2)} placed!`);
      },
      error: err => {
        this.claimError = err?.error || 'Bid failed.';
        this.actionLoading = false;
      }
    });
  }

  getComic(): void {
    const id = parseInt(this.route.snapshot.paramMap.get('id')!, 10);
    this.comicService.getComic(id)
      .subscribe(comic => {
        this.comic = comic;
        this.loading = false;
        if (comic?.bidStartedAt) {
          const endsAt = new Date(comic.bidStartedAt).getTime() +
                         this.configService.biddingCycleMins * 60000;
          this.bidSecondsRemaining = Math.max(0, Math.floor((endsAt - Date.now()) / 1000));
          if (this.bidSecondsRemaining > 0) this.startBidTimer();
        }
        // Start polling/event subscription so other users' bids push updates here
        if (comic?.enableBid) {
          this.setupBidRefresh();
        }
      });
  }

  /** Subscribe to claim events and poll so bid state stays current for all viewers. */
  private setupBidRefresh(): void {
    // Event-driven: react immediately when a bid notification arrives for this comic
    if (!this.claimEventSub) {
      this.claimEventSub = this.toastService.newClaimEvent$.subscribe(n => {
        if (this.comic && n.comicId === String(this.comic.id)) {
          this.refreshBidState();
        }
      });
    }
    // Polling fallback: catch anything the notification stream may have missed
    if (!this.bidPollInterval) {
      this.bidPollInterval = setInterval(() => this.refreshBidState(), 5000);
    }
  }

  /** Re-fetch the comic and update bid state without disrupting an already-running timer. */
  private refreshBidState(): void {
    if (!this.comic || this.actionLoading) return;
    const wasActive = this.isBiddingActive();
    this.comicService.getComic(this.comic.id).subscribe({
      next: latestComic => {
        if (!latestComic) return;
        this.comic = { ...this.comic!, ...latestComic };
        // If bidding just became visible (another user started it), kick off the timer
        if (!wasActive && this.comic.bidStartedAt) {
          const endsAt = new Date(this.comic.bidStartedAt).getTime() +
                         this.configService.biddingCycleMins * 60000;
          this.bidSecondsRemaining = Math.max(0, Math.floor((endsAt - Date.now()) / 1000));
          if (this.bidSecondsRemaining > 0) this.startBidTimer();
        }
        // If bidding was already active, the running timer self-corrects each tick
        // because it reads this.comic.bidStartedAt live — so a bid reset is picked
        // up automatically on the very next 1-second tick.
      },
      error: () => {}
    });
  }

  toggleZoom(): void {
    this.zoomOpen = !this.zoomOpen;
  }

  onImageFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length || !this.comic) return;
    const file = input.files[0];
    this.imageUploading = true;
    this.imageUploadErrorSummary = '';
    this.imageUploadErrorDetail = '';
    this.imageUploadErrorExpanded = false;
    this.imageService.uploadComicImage(this.comic.id, file).subscribe({
      next: (updatedComic: Comic) => {
        this.imageUploading = false;
        if (this.comic) {
          this.comic.largeCachedImageId = updatedComic.largeCachedImageId;
          this.comic.smallCachedImageId = updatedComic.smallCachedImageId;
        }
        input.value = '';
      },
      error: (err: any) => {
        this.imageUploading = false;
        const sizeMB = (file.size / 1024 / 1024).toFixed(1);
        this.imageUploadErrorSummary = `Upload failed (${sizeMB} MB).`;
        this.imageUploadErrorDetail = err?.error || err?.message || 'Image may be too large or an invalid format.';
        input.value = '';
      }
    });
  }

  onBackImageFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length || !this.comic) return;
    const file = input.files[0];
    this.backImageUploading = true;
    this.backImageUploadErrorSummary = '';
    this.backImageUploadErrorDetail = '';
    this.backImageUploadErrorExpanded = false;
    this.imageService.uploadComicBackImage(this.comic.id, file).subscribe({
      next: (updatedComic: Comic) => {
        this.backImageUploading = false;
        if (this.comic) {
          this.comic.largeBackImageId = updatedComic.largeBackImageId;
          this.comic.smallBackImageId = updatedComic.smallBackImageId;
        }
        input.value = '';
      },
      error: (err: any) => {
        this.backImageUploading = false;
        const sizeMB = (file.size / 1024 / 1024).toFixed(1);
        this.backImageUploadErrorSummary = `Upload failed (${sizeMB} MB).`;
        this.backImageUploadErrorDetail = err?.error || err?.message || 'Image may be too large or an invalid format.';
        input.value = '';
      }
    });
  }

  goBack(): void {
    this.location.back();
  }

  copyShareLink(): void {
    navigator.clipboard.writeText(window.location.href).then(() => {
      this.linkCopied = true;
      setTimeout(() => this.linkCopied = false, 2000);
    });
  }

  getSmallImageURLByName(imageName: string | null | undefined): Observable<string> {
    if (!imageName) return of('assets/comic-book-small.png');
    return of(this.imageService.getRemoteImageURLByName(imageName));
  }

  getFullImageURLByName(imageName: string | null | undefined): Observable<string> {
      if (!imageName) {
          return of('assets/comic-book-large.png');
      }
      return of(this.imageService.getRemoteImageURLByName(imageName));
  }

  getGrandComicDatabaseURL(gcdbId: number | null | undefined): Observable<string> {
      if (gcdbId == null) {
          return of('https://www.comics.org/');
      }
      return of('https://www.comics.org/issue/' + gcdbId);
  }

  getGrandComicSeriesDatabaseURL(gcdbSeries: number | null | undefined): Observable<string> {
      if (gcdbSeries == null) {
          return of('https://www.comics.org/');
      }
      return of('https://www.comics.org/series/' + gcdbSeries);
  }

}