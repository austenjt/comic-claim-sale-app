import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Comic } from '../comic';
import { ComicService } from '../comic.service';
import { CartService } from '../cart.service';
import { AuthService } from '../auth.service';

@Component({
    selector: 'app-trade-board',
    templateUrl: './trade-board.component.html',
    styleUrls: ['./trade-board.component.css'],
    standalone: false
})
export class TradeBoardComponent implements OnInit {
  wantedComics: Comic[] = [];
  loading = false;
  error = '';
  addingTradeId: string | null = null;
  addedTradeIds = new Set<string>();
  tradeErrors: Record<string, string> = {};

  private readonly imageBase = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';

  constructor(
    private comicService: ComicService,
    private cartService: CartService,
    public auth: AuthService,
    private title: Title
  ) {}

  ngOnInit(): void {
    this.title.setTitle('Trade Board — Lightning Comics PDX');
    this.loading = true;
    this.comicService.getWantedComics().subscribe({
      next: comics => { this.wantedComics = comics; this.loading = false; },
      error: () => { this.error = 'Failed to load wanted comics.'; this.loading = false; }
    });
  }

  imageUrl(comic: Comic): string {
    if (comic.smallCachedImageId) {
      return `${this.imageBase}/images/${comic.smallCachedImageId}`;
    }
    return 'assets/comic-book-small.png';
  }

  comicLabel(comic: Comic): string {
    if (!comic.number) return comic.title;
    const n = comic.number.number != null
      ? `#${comic.number.number}`
      : (comic.number.sentinel ?? '');
    return `${comic.title} ${n}`.trim();
  }

  creditValue(comic: Comic): number {
    return Math.abs(comic.salePrice ?? 0);
  }

  comicKey(comic: Comic): string {
    return String(comic.id);
  }

  offerTrade(comic: Comic) {
    const id = this.comicKey(comic);
    this.addingTradeId = id;
    delete this.tradeErrors[id];
    this.cartService.addTradeItem(id).subscribe({
      next: () => {
        this.addedTradeIds.add(id);
        this.addingTradeId = null;
      },
      error: (err) => {
        this.tradeErrors[id] = err?.error || 'Failed to add trade item. You may already have a trade in your cart.';
        this.addingTradeId = null;
      }
    });
  }

  isAdding(comic: Comic): boolean {
    return this.addingTradeId === this.comicKey(comic);
  }

  isAdded(comic: Comic): boolean {
    return this.addedTradeIds.has(this.comicKey(comic));
  }

  canOffer(): boolean {
    return this.auth.isLoggedIn() && !this.auth.isAdmin();
  }
}
