import { Component, DestroyRef, HostListener, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title, Meta } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';

import { Comic } from '../comic';
import { Cart } from '../cart';
import { ComicService } from '../comic.service';
import { ImageService } from '../image.service';
import { CartService } from '../cart.service';
import { AuthService } from '../auth.service';
import { ConfigService, ComicEnums } from '../config.service';
import { DashboardNavService, NavItem } from '../dashboard-nav.service';
import { LogService } from '../log.service';
import { Observable, of } from 'rxjs';
import { tap } from 'rxjs/operators';
import { DocType, ListingType, ERA_OPTIONS } from '../comic.enums';

@Component({
    selector: 'app-comic-detail',
    templateUrl: './comic-detail.component.html',
    styleUrls: ['./comic-detail.component.css'],
    standalone: false
})
export class ComicDetailComponent implements OnInit {

  comic: Comic | undefined;
  activeImage: 'front' | 'back' = 'front';
  zoomOpen = false;
  claimedMap: Record<string, string> = {};
  myCart: Cart | null = null;
  claimError = '';
  loading = true;
  actionLoading = false;
  private destroyRef = inject(DestroyRef);
  imageUploading = false;
  imageUploadErrorSummary = '';
  imageUploadErrorDetail = '';
  imageUploadErrorExpanded = false;
  backImageUploading = false;
  backImageUploadErrorSummary = '';
  backImageUploadErrorDetail = '';
  backImageUploadErrorExpanded = false;
  captureModalOpen = false;
  captureModalTarget: 'front' | 'back' | 'trade-front' | 'trade-back' | null = null;
  tradeImageUploading = false;
  tradeImageUploadError = '';
  tradeBackImageUploading = false;
  tradeBackImageUploadError = '';
  readonly Math = Math;
  readonly eraOptions = ERA_OPTIONS;
  linkCopied = false;
  pendingDelete = false;
  deleting = false;
  editComic: Comic | null = null;
  editGradingCompany = '';
  editWritersStr = '';
  editArtistsStr = '';
  salePriceStr = '';
  saving = false;
  saveError = '';
  saveDone = false;
  selectedTradeGrade: number | null = null;
  tradeError = '';
  enums: ComicEnums = { coverVariants: [], gradingCompanies: [], grades: [], pageQualities: [] };

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private comicService: ComicService,
    private imageService: ImageService,
    private cartService: CartService,
    public auth: AuthService,
    public configService: ConfigService,
    private navService: DashboardNavService,
    private location: Location,
    private titleService: Title,
    private meta: Meta,
    private logService: LogService,
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
    this.enums = this.configService.getEnums();
    // paramMap so navigating detail→detail (same route, different :id) reloads even when
    // Angular reuses the component instance.
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const id = parseInt(params.get('id')!, 10);
      this.resetForNewComic();
      this.loadComic(id);
      this.cartService.getClaimedMap()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ next: m => this.claimedMap = m, error: () => {} });
      if (this.auth.isApproved() && !this.auth.isAdmin()) {
        this.cartService.getMyCart()
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({ next: cart => this.myCart = cart, error: () => {} });
      }
    });
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

  private resetForNewComic(): void {
    this.comic = undefined;
    this.loading = true;
    this.activeImage = 'front';
    this.zoomOpen = false;
    this.claimError = '';
    this.actionLoading = false;
    this.linkCopied = false;
    this.pendingDelete = false;
    this.deleting = false;
    this.editComic = null;
    this.editWritersStr = '';
    this.editArtistsStr = '';
    this.salePriceStr = '';
    this.saving = false;
    this.saveError = '';
    this.saveDone = false;
    this.selectedTradeGrade = null;
    this.tradeError = '';
    this.imageUploading = false;
    this.imageUploadErrorSummary = '';
    this.imageUploadErrorDetail = '';
    this.imageUploadErrorExpanded = false;
    this.backImageUploading = false;
    this.backImageUploadErrorSummary = '';
    this.backImageUploadErrorDetail = '';
    this.backImageUploadErrorExpanded = false;
    this.captureModalOpen = false;
    this.captureModalTarget = null;
    this.tradeImageUploading = false;
    this.tradeImageUploadError = '';
    this.tradeBackImageUploading = false;
    this.tradeBackImageUploadError = '';
  }

  claimedDate(comicId: number): string | null {
    return this.claimedMap[String(comicId)] ?? null;
  }

  isInMyCart(comicId: number): boolean {
    return this.myCart?.items.some(i => i.comicId === String(comicId)) ?? false;
  }

  canClaim(comicId: number): boolean {
    return !this.claimedDate(comicId) &&
           !this.comic?.soldTo &&
           !!this.comic?.salePrice &&
           (this.myCart?.status === 'OPEN' || !this.myCart);
  }

  get previewTradeCredit(): number | null {
    if (!this.selectedTradeGrade || !this.comic?.expectedValue) return null;
    const desiredGrade = this.comic.trade?.desiredGrade;
    if (desiredGrade == null) return null;
    const offeredMult = this.configService.gradeMultiplier(this.selectedTradeGrade);
    const desiredMult = this.configService.gradeMultiplier(desiredGrade);
    if (offeredMult === undefined || !desiredMult) return null;
    return Math.round(this.comic.expectedValue * (offeredMult / desiredMult) * 100) / 100;
  }

  get tradeGradeWarning(): boolean {
    const desired = this.comic?.trade?.desiredGrade;
    if (!desired || !this.selectedTradeGrade) return false;
    return Math.abs(this.selectedTradeGrade - desired) > 2.0;
  }

  canTrade(comicId: number): boolean {
    return !this.claimedDate(comicId) &&
           !this.comic?.soldTo &&
           !!this.comic?.expectedValue &&
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

  tradeIn(): void {
    if (!this.comic || !this.selectedTradeGrade) return;
    this.tradeError = '';
    this.actionLoading = true;
    this.cartService.addTradeItem(String(this.comic.id), this.selectedTradeGrade).subscribe({
      next: cart => {
        this.myCart = cart;
        this.claimedMap[String(this.comic!.id)] = new Date().toISOString();
        this.actionLoading = false;
      },
      error: err => {
        this.tradeError = err?.error || 'Failed to add trade item.';
        this.actionLoading = false;
      }
    });
  }

  release(): void {
    if (!this.comic) return;
    this.claimError = '';
    this.actionLoading = true;
    this.cartService.removeItem(String(this.comic.id)).subscribe({
      next: cart => {
        this.myCart = cart;
        delete this.claimedMap[String(this.comic!.id)];
        this.actionLoading = false;
      },
      error: err => {
        this.claimError = err?.error || 'Failed to release comic.';
        this.actionLoading = false;
      }
    });
  }

  private loadClaimedMap(): void {
    this.cartService.getClaimedMap()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: m => this.claimedMap = m, error: () => {} });
  }

  private buildPageMeta(comic: Comic): void {
    const heading = this.comicHeading;
    const parts: string[] = [];
    if (comic.era) parts.push(comic.era);
    if (comic.comicCondition?.cgcCondition?.grade) parts.push(`CGC ${comic.comicCondition.cgcCondition.grade}`);
    else if (comic.comicCondition?.cbcsCondition?.grade) parts.push(`CBCS ${comic.comicCondition.cbcsCondition.grade}`);
    else if (comic.comicCondition?.notCertifiedGrade) parts.push(`Grade ${comic.comicCondition.notCertifiedGrade}`);
    if (comic.publisher) parts.push(comic.publisher);
    const desc = parts.length
      ? `${heading}. ${parts.join(', ')}. Available for claim at Lightning Comics PDX.`
      : `${heading}. Available for claim at Lightning Comics PDX in Oregon City, OR.`;
    const title = `${heading} — Lightning Comics PDX`;
    const imageId = comic.largeCachedImageId ?? comic.smallCachedImageId;
    const imageUrl = imageId ? this.imageService.getRemoteImageURLByName(imageId) : '';
    const canonical = window.location.origin + window.location.pathname;

    this.titleService.setTitle(title);
    this.meta.updateTag({ name: 'description', content: desc });

    // Open Graph
    this.meta.updateTag({ property: 'og:title', content: title });
    this.meta.updateTag({ property: 'og:description', content: desc });
    this.meta.updateTag({ property: 'og:type', content: 'product' });
    this.meta.updateTag({ property: 'og:url', content: canonical });
    if (imageUrl) this.meta.updateTag({ property: 'og:image', content: imageUrl });

    // Twitter
    this.meta.updateTag({ name: 'twitter:card', content: imageUrl ? 'summary_large_image' : 'summary' });
    this.meta.updateTag({ name: 'twitter:title', content: title });
    this.meta.updateTag({ name: 'twitter:description', content: desc });
    if (imageUrl) this.meta.updateTag({ name: 'twitter:image', content: imageUrl });

    this.updateCanonicalLink(canonical);
    this.updateProductJsonLd(comic, heading, desc, imageUrl, canonical);
  }

  private updateCanonicalLink(href: string): void {
    let link = document.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) {
      link = document.createElement('link');
      link.setAttribute('rel', 'canonical');
      document.head.appendChild(link);
    }
    link.setAttribute('href', href);
  }

  private updateProductJsonLd(comic: Comic, heading: string, description: string, imageUrl: string, canonical: string): void {
    const inStock = !comic.soldTo && !!comic.salePrice;
    const product: Record<string, unknown> = {
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: heading,
      description,
      url: canonical,
      brand: comic.publisher ? { '@type': 'Brand', name: comic.publisher } : undefined,
      sku: String(comic.id),
    };
    if (imageUrl) product['image'] = imageUrl;
    if (comic.salePrice != null) {
      product['offers'] = {
        '@type': 'Offer',
        price: comic.salePrice.toFixed(2),
        priceCurrency: 'USD',
        url: canonical,
        availability: inStock ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock',
      };
    }

    const existing = document.getElementById('comic-jsonld');
    if (existing) existing.remove();
    const script = document.createElement('script');
    script.type = 'application/ld+json';
    script.id = 'comic-jsonld';
    script.text = JSON.stringify(product, (_k, v) => v === undefined ? undefined : v);
    document.head.appendChild(script);
  }

  private initEditComic(comic: Comic): void {
    this.editComic = structuredClone(comic);
    if (!this.editComic.number) {
      this.editComic.number = { volume: null, number: null, sentinel: null };
    }
    if (!this.editComic.comicCondition) {
      this.editComic.comicCondition = {
        isGraded: false, certificationCompany: null, certificationId: null,
        cgcCondition: null, cbcsCondition: null,
        notCertifiedLabel: null, notCertifiedGrade: null, notCertifiedPageQuality: null,
        notCertifiedPedigree: null, notCertifiedDegreeOfRestoration: null, notCertifiedSignature: null
      };
    }
    if (!this.editComic.goCollectInfo) {
      this.editComic.goCollectInfo = { gcIndex: null, gcSlug: null, gcUrl: null, gcSeries: null, importDate: null };
    }
    if (!this.editComic.grandComicDBInfo) {
      this.editComic.grandComicDBInfo = { gcdbIssueId: null, gcdbSeriesId: null, issueUrl: null, seriesUrl: null };
    }
    if (!this.editComic.trade) {
      this.editComic.trade = { desiredGrade: null, offeredGrade: null, offerAccepted: null, tradeReceived: null, tradeNotes: null, offeredBy: null, offeredAt: null, tradeFrontImageId: null, tradeSmallFrontImageId: null, tradeBackImageId: null, tradeSmallBackImageId: null };
    }
    const cond = this.editComic.comicCondition;
    if (cond?.cgcCondition) {
      this.editGradingCompany = 'CGC';
    } else if (cond?.cbcsCondition) {
      this.editGradingCompany = 'CBCS';
    } else {
      this.editGradingCompany = cond?.certificationCompany || 'NOT CERTIFIED';
    }
    this.editWritersStr = (this.editComic.writer ?? []).join(', ');
    this.editArtistsStr = (this.editComic.artist ?? []).join(', ');
    this.salePriceStr = this.editComic.salePrice != null
      ? Number(this.editComic.salePrice).toFixed(2)
      : '';
    // Derive listingType from isForSale for documents that pre-date the listingType field
    if (!this.editComic.listingType) {
      this.editComic.listingType = this.editComic.isForSale ? ListingType.FOR_SALE : ListingType.NOT_LISTED;
    }
    // Set members are always for sale — listingType is controlled by the set container
    if (this.parentSetId !== null) {
      this.editComic.isForSale = true;
      this.editComic.listingType = ListingType.FOR_SALE;
    }
  }

  onGradingCompanyChange(): void {
    const cond = this.editComic?.comicCondition;
    if (!cond) return;
    cond.certificationCompany = this.editGradingCompany;
    if (this.editGradingCompany === 'CGC') {
      cond.isGraded = true;
      if (!cond.cgcCondition) {
        cond.cgcCondition = { label: null, grade: null, pageQuality: null, pedigree: null, signature: null, degreeOfRestoration: null, graderNotes: null };
      }
      cond.cbcsCondition = null;
    } else if (this.editGradingCompany === 'CBCS') {
      cond.isGraded = true;
      cond.cgcCondition = null;
      if (!cond.cbcsCondition) {
        cond.cbcsCondition = { label: null, grade: null, pageQuality: null, pedigree: null, signature: null, degreeOfRestoration: null };
      }
    } else {
      cond.isGraded = false;
      cond.cgcCondition = null;
      cond.cbcsCondition = null;
    }
  }

  saveComic(): void {
    if (!this.editComic) return;
    const writers = this.editWritersStr.split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
    this.editComic.writer = writers.length > 0 ? writers : null;
    const artists = this.editArtistsStr.split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
    this.editComic.artist = artists.length > 0 ? artists : null;
    this.saving = true;
    this.saveError = '';
    this.saveDone = false;
    this.comicService.updateComic(this.editComic).subscribe({
      next: () => {
        this.comic = structuredClone(this.editComic!);
        this.saving = false;
        this.saveDone = true;
        setTimeout(() => this.saveDone = false, 2000);
      },
      error: (err: any) => {
        this.saving = false;
        this.saveError = err?.error || 'Save failed.';
      }
    });
  }

  onSalePriceBlur(): void {
    if (!this.editComic) return;
    const parsed = parseFloat(this.salePriceStr);
    if (!isNaN(parsed) && parsed >= 0) {
      this.editComic.salePrice = parseFloat(parsed.toFixed(2));
      this.salePriceStr = this.editComic.salePrice.toFixed(2);
    } else if (this.salePriceStr.trim() === '') {
      this.editComic.salePrice = null;
      this.salePriceStr = '';
    } else {
      // Revert invalid input to last good value
      this.salePriceStr = this.editComic.salePrice != null
        ? this.editComic.salePrice.toFixed(2)
        : '';
    }
  }

  private loadComic(id: number): void {
    this.comicService.getComic(id)
      .pipe(
        tap(comic => {
          this.comic = comic;
          this.loading = false;
          if (comic) {
            this.buildPageMeta(comic);
            if (this.auth.isAdmin()) this.initEditComic(comic);
            this.maybeRecordView(id);
          }
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  toggleZoom(): void {
    this.zoomOpen = !this.zoomOpen;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.zoomOpen) this.zoomOpen = false;
    else if (this.captureModalOpen) this.closeCaptureModal();
  }

  openCaptureModal(target: 'front' | 'back' | 'trade-front' | 'trade-back'): void {
    this.captureModalTarget = target;
    this.captureModalOpen = true;
  }

  onCaptureFile(file: File): void {
    if (this.captureModalTarget === 'front') {
      this.uploadFrontImage(file);
    } else if (this.captureModalTarget === 'back') {
      this.uploadBackImage(file);
    } else if (this.captureModalTarget === 'trade-front') {
      this.uploadTradeFrontImage(file);
    } else if (this.captureModalTarget === 'trade-back') {
      this.uploadTradeBackImage(file);
    }
    this.captureModalOpen = false;
    this.captureModalTarget = null;
  }

  closeCaptureModal(): void {
    this.captureModalOpen = false;
    this.captureModalTarget = null;
  }

  private maybeRecordView(id: number): void {
    const key = `lc-view-${id}`;
    const last = localStorage.getItem(key);
    const now = Date.now();
    if (last && (now - parseInt(last, 10)) < 24 * 60 * 60 * 1000) {
      return;
    }
    localStorage.setItem(key, String(now));
    this.comicService.recordView(id).subscribe(r => {
      if (this.comic) this.comic.viewCount = r.viewCount;
    });
  }

  clearFrontImage(): void {
    if (!this.editComic) return;
    this.editComic.largeCachedImageId = null;
    this.editComic.smallCachedImageId = null;
    this.saveComic();
  }

  clearBackImage(): void {
    if (!this.editComic) return;
    this.editComic.largeBackImageId = null;
    this.editComic.smallBackImageId = null;
    this.saveComic();
  }

  private uploadFrontImage(file: File): void {
    if (!this.comic) return;
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
      },
      error: (err: any) => {
        this.imageUploading = false;
        const sizeMB = (file.size / 1024 / 1024).toFixed(1);
        this.imageUploadErrorSummary = `Upload failed (${sizeMB} MB).`;
        this.imageUploadErrorDetail = err?.error || err?.message || 'Image may be too large or an invalid format.';
      }
    });
  }

  private uploadBackImage(file: File): void {
    if (!this.comic) return;
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
      },
      error: (err: any) => {
        this.backImageUploading = false;
        const sizeMB = (file.size / 1024 / 1024).toFixed(1);
        this.backImageUploadErrorSummary = `Upload failed (${sizeMB} MB).`;
        this.backImageUploadErrorDetail = err?.error || err?.message || 'Image may be too large or an invalid format.';
      }
    });
  }

  private uploadTradeFrontImage(file: File): void {
    if (!this.comic) return;
    this.tradeImageUploading = true;
    this.tradeImageUploadError = '';
    this.imageService.uploadTradeFrontImage(this.comic.id, file).subscribe({
      next: (updatedComic: Comic) => {
        this.tradeImageUploading = false;
        if (this.comic && updatedComic.trade) {
          if (!this.comic.trade) this.comic.trade = updatedComic.trade;
          this.comic.trade.tradeFrontImageId = updatedComic.trade.tradeFrontImageId;
          this.comic.trade.tradeSmallFrontImageId = updatedComic.trade.tradeSmallFrontImageId;
        }
      },
      error: (err: any) => {
        this.tradeImageUploading = false;
        const sizeMB = (file.size / 1024 / 1024).toFixed(1);
        this.tradeImageUploadError = `Upload failed (${sizeMB} MB). ${err?.error || err?.message || ''}`.trim();
      }
    });
  }

  private uploadTradeBackImage(file: File): void {
    if (!this.comic) return;
    this.tradeBackImageUploading = true;
    this.tradeBackImageUploadError = '';
    this.imageService.uploadTradeBackImage(this.comic.id, file).subscribe({
      next: (updatedComic: Comic) => {
        this.tradeBackImageUploading = false;
        if (this.comic && updatedComic.trade) {
          if (!this.comic.trade) this.comic.trade = updatedComic.trade;
          this.comic.trade.tradeBackImageId = updatedComic.trade.tradeBackImageId;
          this.comic.trade.tradeSmallBackImageId = updatedComic.trade.tradeSmallBackImageId;
        }
      },
      error: (err: any) => {
        this.tradeBackImageUploading = false;
        const sizeMB = (file.size / 1024 / 1024).toFixed(1);
        this.tradeBackImageUploadError = `Upload failed (${sizeMB} MB). ${err?.error || err?.message || ''}`.trim();
      }
    });
  }

  deleteComic(): void {
    if (!this.comic) return;
    if (!this.pendingDelete) {
      this.pendingDelete = true;
      return;
    }
    this.pendingDelete = false;
    this.deleting = true;
    this.comicService.deleteComic(this.comic.id).subscribe({
      next: () => { this.location.back(); },
      error: () => { this.deleting = false; }
    });
  }

  /** Returns the SET container's ID when this comic is a member of a set, null otherwise. */
  get parentSetId(): number | null {
    const group = this.comic?.collectionGroup;
    if (!group || this.comic?.docType === DocType.SET) return null;
    return this.navService.getSetContainerId(group);
  }

  get prevItem(): NavItem | null {
    if (!this.comic) return null;
    return this.navService.getAdjacent(this.comic.id).prev;
  }

  get nextItem(): NavItem | null {
    if (!this.comic) return null;
    return this.navService.getAdjacent(this.comic.id).next;
  }

  get navPosition(): { index: number; total: number } {
    if (!this.comic) return { index: 0, total: 0 };
    return this.navService.getPosition(this.comic.id);
  }

  navigateTo(item: NavItem): void {
    const route = item.docType === DocType.SET ? ['/set', item.id] : ['/detail', item.id];
    this.router.navigate(route);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  goBack(): void {
    this.router.navigate(['/selling']);
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