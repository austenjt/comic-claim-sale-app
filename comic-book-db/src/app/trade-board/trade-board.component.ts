import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Comic } from '../comic';
import { ComicService } from '../comic.service';
import { CartService } from '../cart.service';
import { AuthService } from '../auth.service';
import { ImageService } from '../image.service';

interface GradeOption {
  value: number;
  label: string;
  multiplier: number;
}

const GRADE_OPTIONS: GradeOption[] = [
  { value: 10.0, label: '10.0 — Gem Mint',            multiplier: 3.50 },
  { value:  9.9, label:  '9.9 — Mint',                multiplier: 2.40 },
  { value:  9.8, label:  '9.8 — Near Mint/Mint',      multiplier: 1.25 },
  { value:  9.6, label:  '9.6 — Near Mint+',          multiplier: 1.10 },
  { value:  9.4, label:  '9.4 — Near Mint',           multiplier: 1.00 },
  { value:  9.2, label:  '9.2 — Near Mint-',          multiplier: 0.75 },
  { value:  9.0, label:  '9.0 — Very Fine/Near Mint', multiplier: 0.55 },
  { value:  8.5, label:  '8.5 — Very Fine+',          multiplier: 0.40 },
  { value:  8.0, label:  '8.0 — Very Fine',           multiplier: 0.22 },
  { value:  7.5, label:  '7.5 — Very Fine-',          multiplier: 0.14 },
  { value:  7.0, label:  '7.0 — Fine/Very Fine',      multiplier: 0.13 },
  { value:  6.5, label:  '6.5 — Fine+',               multiplier: 0.12 },
  { value:  6.0, label:  '6.0 — Fine',                multiplier: 0.10 },
  { value:  5.5, label:  '5.5 — Fine-',               multiplier: 0.08 },
  { value:  5.0, label:  '5.0 — Very Good/Fine',      multiplier: 0.06 },
  { value:  4.5, label:  '4.5 — Very Good+',          multiplier: 0.05 },
  { value:  4.0, label:  '4.0 — Very Good',           multiplier: 0.04 },
  { value:  3.5, label:  '3.5 — Very Good-',          multiplier: 0.04 },
  { value:  3.0, label:  '3.0 — Good/Very Good',      multiplier: 0.02 },
  { value:  2.5, label:  '2.5 — Good+',               multiplier: 0.02 },
  { value:  2.0, label:  '2.0 — Good',                multiplier: 0.00 },
  { value:  1.8, label:  '1.8 — Good-',               multiplier: 0.00 },
  { value:  1.5, label:  '1.5 — Fair/Good',           multiplier: 0.00 },
  { value:  1.0, label:  '1.0 — Fair',                multiplier: 0.00 },
  { value:  0.5, label:  '0.5 — Poor',                multiplier: 0.00 },
];

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
  addedTradeIds = new Set<string>();
  claimedMap: Record<string, string> = {};

  // Grade selector modal
  modalComic: Comic | null = null;
  selectedGrade: number = 9.4; // default Near Mint
  submitting = false;
  submitError = '';

  readonly gradeOptions = GRADE_OPTIONS;

  constructor(
    private comicService: ComicService,
    private cartService: CartService,
    public auth: AuthService,
    private imageService: ImageService,
    private title: Title
  ) {}

  ngOnInit(): void {
    this.title.setTitle('Trade Board — Lightning Comics PDX');
    this.loading = true;
    this.comicService.getWantedComics().subscribe({
      next: comics => { this.wantedComics = comics; this.loading = false; },
      error: () => { this.error = 'Failed to load wanted comics.'; this.loading = false; }
    });
    this.cartService.getClaimedMap().subscribe({ next: m => this.claimedMap = m, error: () => {} });
  }

  imageUrl(comic: Comic): string {
    if (comic.smallCachedImageId) {
      return this.imageService.getRemoteImageURLByName(comic.smallCachedImageId);
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

  comicKey(comic: Comic): string {
    return String(comic.id);
  }

  canOffer(): boolean {
    return this.auth.isLoggedIn() && !this.auth.isAdmin();
  }

  isAdded(comic: Comic): boolean {
    return this.addedTradeIds.has(this.comicKey(comic));
  }

  isOfferedByOther(comic: Comic): boolean {
    return !this.isAdded(comic) && !!this.claimedMap[this.comicKey(comic)];
  }

  // ── Grade modal ────────────────────────────────────────────────────────────

  openModal(comic: Comic): void {
    this.modalComic = comic;
    this.selectedGrade = 9.4;
    this.submitError = '';
    this.submitting = false;
  }

  closeModal(): void {
    this.modalComic = null;
    this.submitError = '';
  }

  get previewCredit(): number {
    if (!this.modalComic?.nmEstimatedValue) return 0;
    const opt = GRADE_OPTIONS.find(g => g.value === this.selectedGrade);
    if (!opt) return 0;
    return Math.round(this.modalComic.nmEstimatedValue * opt.multiplier * 100) / 100;
  }

  confirmTrade(): void {
    if (!this.modalComic) return;
    this.submitting = true;
    this.submitError = '';
    const id = this.comicKey(this.modalComic);
    this.cartService.addTradeItem(id, this.selectedGrade).subscribe({
      next: () => {
        this.addedTradeIds.add(id);
        this.submitting = false;
        this.modalComic = null;
      },
      error: (err) => {
        this.submitError = err?.error || 'Failed to add trade item. You may already have a trade in your cart.';
        this.submitting = false;
      }
    });
  }
}
