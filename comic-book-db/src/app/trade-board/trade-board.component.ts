import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { Comic } from '../comic';
import { ComicService } from '../comic.service';
import { CartService } from '../cart.service';
import { AuthService } from '../auth.service';
import { ConfigService, GradeOption } from '../config.service';
import { WantedCardComponent } from '../wanted-card/wanted-card.component';
import { LogService } from '../log.service';

@Component({
    selector: 'app-trade-board',
    templateUrl: './trade-board.component.html',
    styleUrls: ['./trade-board.component.css'],
    standalone: true,
    imports: [CommonModule, FormsModule, RouterModule, WantedCardComponent],
})
export class TradeBoardComponent implements OnInit {
  wantedComics: Comic[] = [];
  loading = false;
  error = '';
  addedTradeIds = new Set<string>();
  claimedMap: Record<string, string> = {};
  copyingId: number | null = null;
  copiedId: number | null = null;

  // Grade selector modal
  modalComic: Comic | null = null;
  selectedGrade: number | null = null;
  submitting = false;
  submitError = '';

  private destroyRef = inject(DestroyRef);

  trackById(_index: number, c: Comic): number { return c.id; }

  constructor(
    private comicService: ComicService,
    private cartService: CartService,
    public auth: AuthService,
    private configService: ConfigService,
    private logService: LogService,
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
    return this.auth.isLoggedIn();
  }

  isAdded(comic: Comic): boolean {
    return this.addedTradeIds.has(this.comicKey(comic));
  }

  isOfferedByOther(comic: Comic): boolean {
    return !this.isAdded(comic) && !!this.claimedMap[this.comicKey(comic)];
  }

  // ── Admin: duplicate comic ──────────────────────────────────────────────────

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
      next: (created) => {
        this.copyingId = null;
        this.copiedId = comic.id;
        this.wantedComics.push(created);
        setTimeout(() => { this.copiedId = null; }, 2000);
      },
      error: () => { this.copyingId = null; }
    });
  }

  // ── Grade modal ────────────────────────────────────────────────────────────

  openModal(comic: Comic): void {
    this.modalComic = comic;
    this.selectedGrade = null;
    this.submitError = '';
    this.submitting = false;
  }

  closeModal(): void {
    this.modalComic = null;
    this.submitError = '';
  }

  get previewCredit(): number {
    if (!this.modalComic?.expectedValue || this.selectedGrade == null) return 0;
    const desiredGrade = this.modalComic.trade?.desiredGrade;
    if (desiredGrade == null) return 0;
    const offeredMult = this.configService.gradeMultiplier(this.selectedGrade);
    const desiredMult = this.configService.gradeMultiplier(desiredGrade);
    if (!desiredMult) return 0;
    return Math.round(this.modalComic.expectedValue * (offeredMult / desiredMult) * 100) / 100;
  }

  get gradeWarning(): boolean {
    const desired = this.modalComic?.trade?.desiredGrade;
    if (desired == null || this.selectedGrade == null) return false;
    const grades = this.gradeOptions;
    const desiredIdx = grades.findIndex(g => g.value === desired);
    const selectedIdx = grades.findIndex(g => g.value === this.selectedGrade);
    if (desiredIdx < 0 || selectedIdx < 0) return false;
    return Math.abs(desiredIdx - selectedIdx) > 2;
  }

  confirmTrade(): void {
    if (!this.modalComic || this.selectedGrade == null) return;
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
