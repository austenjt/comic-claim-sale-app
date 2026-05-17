import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Comic } from '../comic';
import { ComicService } from '../comic.service';
import { CartService } from '../cart.service';
import { AuthService } from '../auth.service';
import { ImageService } from '../image.service';
import { ConfigService, GradeOption } from '../config.service';

@Component({
    selector: 'app-trade-board',
    templateUrl: './trade-board.component.html',
    styleUrls: ['./trade-board.component.css'],
    standalone: true,
    imports: [CommonModule, FormsModule],
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

  trackById(_index: number, c: Comic): number { return c.id; }

  constructor(
    private comicService: ComicService,
    private cartService: CartService,
    public auth: AuthService,
    private imageService: ImageService,
    private configService: ConfigService,
    private title: Title
  ) {}

  get gradeOptions(): GradeOption[] {
    return this.configService.getEnums().grades;
  }

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
    const multiplier = this.configService.gradeMultiplier(this.selectedGrade);
    return Math.round(this.modalComic.nmEstimatedValue * multiplier * 100) / 100;
  }

  get gradeWarning(): boolean {
    const desired = this.modalComic?.trade?.desiredGrade;
    if (!desired) return false;
    return Math.abs(this.selectedGrade - desired) > 2.0;
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
