import { Component, OnInit, OnDestroy } from '@angular/core';
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
import { Observable, of, map, Subscription } from 'rxjs';

@Component({
    selector: 'app-comic-detail',
    templateUrl: './comic-detail.component.html',
    styleUrls: ['./comic-detail.component.css'],
    standalone: false
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
  private routeParamSub: Subscription | null = null;
  imageUploading = false;
  imageUploadErrorSummary = '';
  imageUploadErrorDetail = '';
  imageUploadErrorExpanded = false;
  backImageUploading = false;
  backImageUploadErrorSummary = '';
  backImageUploadErrorDetail = '';
  backImageUploadErrorExpanded = false;
  captureModalOpen = false;
  captureModalTarget: 'front' | 'back' | null = null;
  readonly Math = Math;
  linkCopied = false;
  pendingDelete = false;
  deleting = false;
  editComic: Comic | null = null;
  editWritersStr = '';
  editArtistsStr = '';
  salePriceStr = '';
  saving = false;
  saveError = '';
  saveDone = false;
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
    // Subscribe to paramMap so navigating detail→detail (same route, different :id)
    // properly reloads the comic even when Angular reuses the component instance.
    this.routeParamSub = this.route.paramMap.subscribe(params => {
      const id = parseInt(params.get('id')!, 10);
      this.resetForNewComic();
      this.loadComic(id);
      this.cartService.getClaimedMap().subscribe({ next: m => this.claimedMap = m, error: () => {} });
      if (this.auth.isApproved() && !this.auth.isAdmin()) {
        this.cartService.getMyCart().subscribe({ next: cart => this.myCart = cart, error: () => {} });
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

  ngOnDestroy(): void {
    if (this.routeParamSub) this.routeParamSub.unsubscribe();
  }

  private loadClaimedMap(): void {
    this.cartService.getClaimedMap().subscribe({ next: m => this.claimedMap = m, error: () => {} });
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
    this.titleService.setTitle(`${heading} — Lightning Comics PDX`);
    this.meta.updateTag({ name: 'description', content: desc });
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
    this.editWritersStr = (this.editComic.writer ?? []).join(', ');
    this.editArtistsStr = (this.editComic.artist ?? []).join(', ');
    this.salePriceStr = this.editComic.salePrice != null
      ? Number(this.editComic.salePrice).toFixed(2)
      : '';
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
      .subscribe(comic => {
        this.comic = comic;
        this.loading = false;
        if (comic) {
          this.buildPageMeta(comic);
          this.comicService.recordView(id).subscribe(r => {
            if (this.comic) this.comic.viewCount = r.viewCount;
          });
        }
        if (comic && this.auth.isAdmin()) this.initEditComic(comic);
      });
  }

  toggleZoom(): void {
    this.zoomOpen = !this.zoomOpen;
  }

  openCaptureModal(target: 'front' | 'back'): void {
    this.captureModalTarget = target;
    this.captureModalOpen = true;
  }

  onCaptureFile(file: File): void {
    if (this.captureModalTarget === 'front') {
      this.uploadFrontImage(file);
    } else if (this.captureModalTarget === 'back') {
      this.uploadBackImage(file);
    }
    this.captureModalOpen = false;
    this.captureModalTarget = null;
  }

  closeCaptureModal(): void {
    this.captureModalOpen = false;
    this.captureModalTarget = null;
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
    if (!group || this.comic?.docType === 'SET') return null;
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
    const route = item.docType === 'SET' ? ['/set', item.id] : ['/detail', item.id];
    this.router.navigate(route);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
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