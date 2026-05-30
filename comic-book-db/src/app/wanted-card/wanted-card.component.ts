import { Component, EventEmitter, HostBinding, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Comic } from '../comic';
import { ImageService } from '../image.service';

@Component({
    selector: 'app-wanted-card',
    templateUrl: './wanted-card.component.html',
    styleUrls: ['./wanted-card.component.css'],
    standalone: true,
    imports: [CommonModule, RouterModule],
})
export class WantedCardComponent {
  @Input() comic!: Comic;
  @Input() isAdded = false;
  @Input() isOfferedByOther = false;
  @Input() isCopying = false;
  @Input() isCopied = false;
  @Input() canOffer = false;
  @Input() isAdmin = false;

  @Input() set expanded(value: boolean) { this._expanded = value; }
  get expanded(): boolean { return this._expanded; }
  @HostBinding('class.expanded') private _expanded = false;

  @Output() offerRequested = new EventEmitter<void>();
  @Output() copyRequested = new EventEmitter<void>();
  @Output() expandRequested = new EventEmitter<void>();

  constructor(private imageService: ImageService) {}

  onExpandClick(): void {
    this.expandRequested.emit();
  }

  get imageUrl(): string {
    if (this.comic.smallCachedImageId) {
      return this.imageService.getRemoteImageURLByName(this.comic.smallCachedImageId);
    }
    return 'assets/comic-book-small.png';
  }

  get label(): string {
    if (!this.comic.number) return this.comic.title;
    const n = this.comic.number.number != null
      ? `#${this.comic.number.number}`
      : (this.comic.number.sentinel ?? '');
    return `${this.comic.title} ${n}`.trim();
  }
}
