import { Component, OnInit, OnDestroy } from '@angular/core';
import { Title, Meta } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { Observable, of, Subscription } from 'rxjs';

import { Comic } from '../comic';
import { Cart } from '../cart';
import { ComicService } from '../comic.service';
import { ImageService } from '../image.service';
import { CartService } from '../cart.service';
import { LogService } from '../log.service';
import { AuthService } from '../auth.service';
import { DashboardNavService, NavItem } from '../dashboard-nav.service';

@Component({
    selector: 'app-set-detail',
    templateUrl: './set-detail.component.html',
    styleUrls: ['./set-detail.component.css'],
    standalone: false
})
export class SetDetailComponent implements OnInit, OnDestroy {

  container: Comic | undefined;
  setMembers: Comic[] = [];
  activeImage: 'front' | 'back' = 'front';
  claimedMap: Record<string, string> = {};
  myCart: Cart | null = null;
  claimError = '';
  actionLoading = false;
  loading = true;
  imageUploading = false;
  imageUploadError = '';
  backImageUploading = false;
  backImageUploadError = '';
  linkCopied = false;
  captureModalOpen = false;
  captureModalTarget: 'front' | 'back' | null = null;

  removingId: number | null = null;
  editContainer: Comic | null = null;
  saving = false;
  saveError = '';
  saveDone = false;

  // ── Add Book to Set modal ──────────────────────────────────────────────
  showAddBookModal = false;
  addBookSearchTerm = '';
  addBookSearchResults: Comic[] = [];
  addBookSearching = false;
  addBookSaving = false;
  addBookError = '';
  zoomOpen = false;
  private addBookSearchTimer: any = null;
  private routeParamSub: Subscription | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private comicService: ComicService,
    private imageService: ImageService,
    private cartService: CartService,
    private logService: LogService,
    public auth: AuthService,
    private navService: DashboardNavService,
    private location: Location,
    private titleService: Title,
    private meta: Meta,
  ) {}

  ngOnInit(): void {
    // Subscribe to paramMap so navigating set→set (same route, different :id)
    // properly reloads the container even when Angular reuses the component instance.
    this.routeParamSub = this.route.paramMap.subscribe(params => {
      const id = parseInt(params.get('id')!, 10);
      this.container = undefined;
      this.setMembers = [];
      this.loading = true;
      this.claimError = '';
      this.editContainer = null;
      this.linkCopied = false;
      this.activeImage = 'front';

      this.comicService.getComic(id).subscribe(comic => {
        this.container = comic;
        this.setMembers = comic?.items ?? [];
        this.loading = false;
        if (comic) {
          // Populate nav so Prev/Next work when viewing member comics.
          // Include the container first (docType='SET') so getSetContainerId() still resolves
          // the "View Set" link on the comic-detail page.
          const members = (comic.items ?? []).filter(m => m.docType !== 'SET');
          this.navService.setList([comic, ...members]);
          this.editContainer = structuredClone(comic);
          const count = (comic.items ?? []).length;
          this.titleService.setTitle(`${comic.title} Set — Lightning Comics PDX`);
          this.meta.updateTag({ name: 'description', content: `${comic.title} — set of ${count} comic${count !== 1 ? 's' : ''} available for claim at Lightning Comics PDX in Oregon City, OR.` });
        }
      });

      this.cartService.getClaimedMap().subscribe({ next: m => this.claimedMap = m, error: () => {} });
      if (this.auth.isApproved()) {
        this.cartService.getMyCart().subscribe({ next: cart => this.myCart = cart, error: () => {} });
      }
    });
  }

  ngOnDestroy(): void {
    if (this.routeParamSub) this.routeParamSub.unsubscribe();
    if (this.addBookSearchTimer) clearTimeout(this.addBookSearchTimer);
  }

  get totalPrice(): number {
    return this.setMembers.reduce((sum, m) => sum + (m.salePrice ?? 0), 0);
  }

  get totalCost(): number {
    return this.setMembers.reduce((sum, m) => sum + (m.pricePaid ?? 0), 0);
  }

  get activeLargeImageId(): string | null | undefined {
    return this.activeImage === 'front' ? this.container?.largeCachedImageId : this.container?.largeBackImageId;
  }

  selectImage(which: 'front' | 'back'): void {
    this.activeImage = which;
  }

  toggleZoom(): void {
    this.zoomOpen = !this.zoomOpen;
  }

  get displayMembers(): Comic[] {
    return this.setMembers.filter(m => m.docType !== 'SET');
  }

  comicNumberLabel(comic: Comic): string {
    const n = comic.number;
    if (!n) return '';
    if (n.number != null) return ` #${n.number}`;
    if (n.sentinel) return ` #${n.sentinel}`;
    return '';
  }

  isInMyCart(comicId: number): boolean {
    return this.myCart?.items.some(i => i.comicId === String(comicId)) ?? false;
  }

  isSetInMyCart(): boolean {
    const containerInCart = this.container ? this.isInMyCart(this.container.id) : false;
    return containerInCart || this.setMembers.some(m => this.isInMyCart(m.id));
  }

  isSetClaimedByOther(): boolean {
    const containerClaimed = this.container
      ? (!!this.claimedMap[String(this.container.id)] && !this.isInMyCart(this.container.id))
      : false;
    return containerClaimed || this.setMembers.some(m =>
      !!this.claimedMap[String(m.id)] && !this.isInMyCart(m.id)
    );
  }

  /**
   * Whether the Claim Set button should be rendered at all.
   * Mirrors the dashboard pattern: visible whenever the set is claimable in
   * principle (has priced members, not already in someone's cart). The
   * button is shown even when the user's cart is not OPEN — in that case
   * {@link canClaimSet} returns false and the button renders disabled.
   */
  showClaimSetButton(): boolean {
    return this.container?.isForSale === true &&
           this.setMembers.length > 0 &&
           this.totalPrice > 0 &&
           !this.isSetInMyCart() &&
           !this.isSetClaimedByOther();
  }

  /** Whether clicking the Claim Set button right now would actually succeed. */
  canClaimSet(): boolean {
    return this.showClaimSetButton() &&
           (this.myCart?.status === 'OPEN' || !this.myCart);
  }

  /** Tooltip explaining why the Claim button is disabled when the user has a non-OPEN cart. */
  cartLockedTitle(): string {
    const status = this.myCart?.status;
    if (status === 'FINALIZING') return 'Your order has been submitted and is in the review window.';
    if (status === 'FINALIZED') return 'Your order is finalized — wait for fulfillment before claiming again.';
    return '';
  }

  claimSet(): void {
    if (!this.container) return;
    this.claimError = '';
    this.actionLoading = true;
    this.cartService.addSet(String(this.container.id)).subscribe({
      next: cart => {
        this.myCart = cart;
        if (this.container) {
          this.claimedMap[String(this.container.id)] = new Date().toISOString();
        }
        for (const m of this.setMembers) {
          this.claimedMap[String(m.id)] = new Date().toISOString();
        }
        this.actionLoading = false;
        const claimName = this.auth.currentUser$.value?.name ?? 'User';
        this.logService.log(`"${this.container!.title}" set (${this.setMembers.length} books) added to ${claimName}'s cart.`);
      },
      error: err => {
        this.claimError = err?.error || 'Failed to claim set.';
        this.actionLoading = false;
      }
    });
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
    if (!this.container) return;
    this.imageUploading = true;
    this.imageUploadError = '';
    this.imageService.uploadComicImage(this.container.id, file).subscribe({
      next: (updatedComic: Comic) => {
        this.imageUploading = false;
        if (this.container) {
          this.container.largeCachedImageId = updatedComic.largeCachedImageId;
          this.container.smallCachedImageId = updatedComic.smallCachedImageId;
        }
      },
      error: () => {
        this.imageUploading = false;
        this.imageUploadError = 'Upload failed. Image may be too large or an invalid format.';
      }
    });
  }

  private uploadBackImage(file: File): void {
    if (!this.container) return;
    this.backImageUploading = true;
    this.backImageUploadError = '';
    this.imageService.uploadComicBackImage(this.container.id, file).subscribe({
      next: (updatedComic: Comic) => {
        this.backImageUploading = false;
        if (this.container) {
          this.container.largeBackImageId = updatedComic.largeBackImageId;
          this.container.smallBackImageId = updatedComic.smallBackImageId;
        }
      },
      error: () => {
        this.backImageUploading = false;
        this.backImageUploadError = 'Upload failed. Image may be too large or an invalid format.';
      }
    });
  }

  removeFromSet(comic: Comic): void {
    this.removingId = comic.id;
    const updated: Comic = { ...comic, collectionGroup: null };
    this.comicService.updateComic(updated).subscribe({
      next: () => {
        this.setMembers = this.setMembers.filter(m => m.id !== comic.id);
        this.removingId = null;
      },
      error: () => { this.removingId = null; }
    });
  }

  openAddBookModal(): void {
    this.addBookSearchTerm = '';
    this.addBookSearchResults = [];
    this.addBookSearching = false;
    this.addBookError = '';
    this.showAddBookModal = true;
  }

  filterAddBookResults(): void {
    if (this.addBookSearchTimer) clearTimeout(this.addBookSearchTimer);
    const term = this.addBookSearchTerm.trim();
    if (!term) {
      this.addBookSearchResults = [];
      this.addBookSearching = false;
      return;
    }
    this.addBookSearching = true;
    this.addBookSearchTimer = setTimeout(() => {
      this.comicService.searchComics(term).subscribe(results => {
        this.addBookSearchResults = results.filter(
          c => c.docType !== 'SET' && (!c.collectionGroup || c.collectionGroup <= 0)
        );
        this.addBookSearching = false;
      });
    }, 300);
  }

  confirmAddBook(comic: Comic): void {
    if (!this.container) return;
    this.addBookSaving = true;
    this.addBookError = '';
    const updated: Comic = { ...comic, collectionGroup: this.container.collectionGroup };
    this.comicService.updateComic(updated).subscribe({
      next: () => {
        this.setMembers = [...this.setMembers, updated];
        this.addBookSaving = false;
        this.showAddBookModal = false;
      },
      error: () => {
        this.addBookError = 'Failed to add book to set. Please try again.';
        this.addBookSaving = false;
      }
    });
  }

  saveContainer(): void {
    if (!this.editContainer) return;
    this.saving = true;
    this.saveError = '';
    this.saveDone = false;
    this.comicService.updateComic(this.editContainer).subscribe({
      next: () => {
        this.container = structuredClone(this.editContainer!);
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

  get prevItem(): NavItem | null {
    if (!this.container) return null;
    return this.navService.getAdjacent(this.container.id).prev;
  }

  get nextItem(): NavItem | null {
    if (!this.container) return null;
    return this.navService.getAdjacent(this.container.id).next;
  }

  get navPosition(): { index: number; total: number } {
    if (!this.container) return { index: 0, total: 0 };
    return this.navService.getPosition(this.container.id);
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

  getImageURL(imageName: string | null | undefined): Observable<string> {
    if (!imageName) return of('assets/comic-book-large.png');
    return of(this.imageService.getRemoteImageURLByName(imageName));
  }

  getSmallImageURL(imageName: string | null | undefined): Observable<string> {
    if (!imageName) return of('assets/comic-book-small.png');
    return of(this.imageService.getRemoteImageURLByName(imageName));
  }

  getMemberImageURL(imageName: string | null | undefined): Observable<string> {
    if (!imageName) return of('assets/comic-book-small.png');
    return of(this.imageService.getRemoteImageURLByName(imageName));
  }
}
