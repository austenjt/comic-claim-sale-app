import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Comic } from '../comic';
import { ImageService } from '../image.service';

@Component({
    selector: 'app-selling-card',
    templateUrl: './selling-card.component.html',
    styleUrls: ['./selling-card.component.css'],
    standalone: false,
})
export class SellingCardComponent {
  @Input() comic!: Comic;

  // Precomputed state from parent
  @Input() isInMyCart = false;
  @Input() isClaimedByOther = false;
  @Input() isSetInMyCart = false;
  @Input() isSetClaimedByOther = false;
  @Input() canClaim = false;
  @Input() canClaimSet = false;
  @Input() showClaimButton = false;
  @Input() showClaimSetButton = false;
  @Input() claimedDate: string | null = null;
  @Input() cartLockedTitle = '';
  @Input() isClaimingSet = false;
  @Input() isCopying = false;
  @Input() isCopied = false;
  @Input() isDeleting = false;

  // Auth / config passed from parent
  @Input() isApproved = false;
  @Input() isAdmin = false;
  @Input() awardModeEnabled = false;

  @Input() expanded = false;

  @Output() claimRequested = new EventEmitter<void>();
  @Output() claimSetRequested = new EventEmitter<void>();
  @Output() markForSaleRequested = new EventEmitter<void>();
  @Output() copyRequested = new EventEmitter<void>();
  @Output() awardRequested = new EventEmitter<void>();
  @Output() expandRequested = new EventEmitter<void>();

  constructor(private imageService: ImageService) {}

  onExpandClick(e: Event): void {
    e.stopPropagation();
    e.preventDefault();
    this.expandRequested.emit();
  }

  get setPrice(): number {
    return (this.comic.items ?? []).reduce((sum, m) => sum + (Number(m.salePrice) || 0), 0);
  }

  get numberLabel(): string {
    const n = this.comic.number;
    if (!n) return '';
    if (n.number != null) return ` #${n.number}`;
    if (n.sentinel) return ` #${n.sentinel}`;
    return '';
  }

  imageUrl(name: string | null | undefined): Observable<string> {
    if (!name) return of('assets/comic-book-small.png');
    return of(this.imageService.getRemoteImageURLByName(name));
  }

  /** Stops event from bubbling to the parent <a> before emitting an output. */
  act(e: Event, output: EventEmitter<void>): void {
    e.preventDefault();
    e.stopPropagation();
    output.emit();
  }
}
