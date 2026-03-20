import { Component, OnInit } from '@angular/core';
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
export class DashboardComponent implements OnInit {

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
  excludeClaimed = false;
  showPricedOnly = false;

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
            },
            error: () => { this.loading = false; }
          });
          return;
        }
        this.comics = comics.filter(c => c.isForSale !== false && !c.dateSold);
        this.loading = false;
      },
      error: () => { this.loading = false; }
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
        this.toastService.show(`"${container.title}" set (${members.length} books) added to your cart.`);
      },
      error: (err) => {
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
