import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';

import { Comic } from '../comic';
import { Cart } from '../cart';
import { ComicService } from '../comic.service';
import { ImageService } from '../image.service';
import { CartService } from '../cart.service';
import { ToastService } from '../toast.service';
import { AuthService } from '../auth.service';
import { Observable, of, map } from 'rxjs';

@Component({
  selector: 'app-comic-detail',
  templateUrl: './comic-detail.component.html',
  styleUrls: [ './comic-detail.component.css' ]
})
export class ComicDetailComponent implements OnInit {

  comic: Comic | undefined;
  activeImage: 'front' | 'back' = 'front';
  zoomOpen = false;
  claimedMap: Record<string, string> = {};
  myCart: Cart | null = null;
  claimError = '';
  actionLoading = false;
  imageUploading = false;
  imageUploadError = '';
  backImageUploading = false;
  backImageUploadError = '';
  linkCopied = false;

  constructor(
    private route: ActivatedRoute,
    private comicService: ComicService,
    private imageService: ImageService,
    private cartService: CartService,
    private toastService: ToastService,
    public auth: AuthService,
    private location: Location
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
    this.getComic();
    this.cartService.getClaimedMap().subscribe({ next: m => this.claimedMap = m, error: () => {} });
    if (this.auth.isApproved() && !this.auth.isAdmin()) {
      this.cartService.getMyCart().subscribe({ next: cart => this.myCart = cart, error: () => {} });
    }
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
    const heading = this.comicHeading;
    this.cartService.removeItem(String(this.comic.id)).subscribe({
      next: cart => {
        this.myCart = cart;
        delete this.claimedMap[String(this.comic!.id)];
        this.actionLoading = false;
        this.toastService.show(`"${heading}" Returned to sale`);
      },
      error: err => {
        this.claimError = err?.error || 'Failed to release comic.';
        this.actionLoading = false;
      }
    });
  }

  getComic(): void {
    const id = parseInt(this.route.snapshot.paramMap.get('id')!, 10);
    this.comicService.getComic(id)
      .subscribe(comic => this.comic = comic);
  }

  toggleZoom(): void {
    this.zoomOpen = !this.zoomOpen;
  }

  onImageFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length || !this.comic) return;
    const file = input.files[0];
    this.imageUploading = true;
    this.imageUploadError = '';
    this.imageService.uploadComicImage(this.comic.id, file).subscribe({
      next: (updatedComic: Comic) => {
        this.imageUploading = false;
        if (this.comic) {
          this.comic.largeCachedImageId = updatedComic.largeCachedImageId;
          this.comic.smallCachedImageId = updatedComic.smallCachedImageId;
        }
        input.value = '';
      },
      error: () => {
        this.imageUploading = false;
        this.imageUploadError = 'Upload failed. Image may be too large or an invalid format.';
        input.value = '';
      }
    });
  }

  onBackImageFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length || !this.comic) return;
    const file = input.files[0];
    this.backImageUploading = true;
    this.backImageUploadError = '';
    this.imageService.uploadComicBackImage(this.comic.id, file).subscribe({
      next: (updatedComic: Comic) => {
        this.backImageUploading = false;
        if (this.comic) {
          this.comic.largeBackImageId = updatedComic.largeBackImageId;
          this.comic.smallBackImageId = updatedComic.smallBackImageId;
        }
        input.value = '';
      },
      error: () => {
        this.backImageUploading = false;
        this.backImageUploadError = 'Upload failed. Image may be too large or an invalid format.';
        input.value = '';
      }
    });
  }

  goBack(): void {
    this.location.back();
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