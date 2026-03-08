import { Component, OnInit } from '@angular/core';
import { Comic } from '../comic';
import { ComicService } from '../comic.service';
import { ImageService } from '../image.service';
import { AuthService } from '../auth.service';
import { CartService } from '../cart.service';
import { ToastService } from '../toast.service';
import { UserService } from '../user.service';
import { AdminSettingsService } from '../admin-settings.service';
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

  constructor(
    private comicService: ComicService,
    private imageService: ImageService,
    public auth: AuthService,
    private cartService: CartService,
    private toastService: ToastService,
    private userService: UserService,
    public adminSettings: AdminSettingsService
  ) {}

  ngOnInit(): void {
    this.getRemoteComics();
    this.loadClaimedMap();
    if (this.auth.isApproved()) {
      this.cartService.getMyCart().subscribe({ next: cart => this.myCart = cart, error: () => {} });
    }
  }

  getRemoteComics(): void {
    this.loading = true;
    this.comicService.getRemoteComics().subscribe({
      next: comics => {
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

  comicNumberLabel(comic: Comic): string {
    const n = comic.number;
    if (!n) return '';
    if (n.number != null) return ` #${n.number}`;
    if (n.sentinel) return ` #${n.sentinel}`;
    return '';
  }

  claim(comic: Comic): void {
    this.claimError = '';
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
        this.claimError = err?.error || 'Failed to claim comic.';
      }
    });
  }

  isInMyCart(comicId: number): boolean {
    return this.myCart?.items.some(i => i.comicId === String(comicId)) ?? false;
  }

  isClaimedByOther(comicId: number): boolean {
    return !!this.claimedMap[String(comicId)] && !this.isInMyCart(comicId);
  }

  canClaim(comicId: number): boolean {
    return !this.isInMyCart(comicId) &&
           !this.isClaimedByOther(comicId) &&
           (this.myCart?.status === 'OPEN' || !this.myCart);
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

  getFullImageURLByName(imageName: string | null | undefined): Observable<string> {
    if (!imageName) {
      return of('assets/comic-book-small.png');
    }
    return of(this.imageService.getRemoteImageURLByName(imageName));
  }

}
