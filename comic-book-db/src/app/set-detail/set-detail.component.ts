import { Component, OnInit } from '@angular/core';
import { Title, Meta } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { Observable, of } from 'rxjs';

import { Comic } from '../comic';
import { Cart } from '../cart';
import { ComicService } from '../comic.service';
import { ImageService } from '../image.service';
import { CartService } from '../cart.service';
import { LogService } from '../log.service';
import { AuthService } from '../auth.service';

@Component({
    selector: 'app-set-detail',
    templateUrl: './set-detail.component.html',
    styleUrls: ['./set-detail.component.css'],
    standalone: false
})
export class SetDetailComponent implements OnInit {

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

  constructor(
    private route: ActivatedRoute,
    private comicService: ComicService,
    private imageService: ImageService,
    private cartService: CartService,
    private logService: LogService,
    public auth: AuthService,
    private location: Location,
    private titleService: Title,
    private meta: Meta,
  ) {}

  ngOnInit(): void {
    const id = parseInt(this.route.snapshot.paramMap.get('id')!, 10);

    this.comicService.getComic(id).subscribe(comic => {
      this.container = comic;
      this.setMembers = comic?.items ?? [];
      this.loading = false;
      if (comic) {
        const count = (comic.items ?? []).length;
        this.titleService.setTitle(`${comic.title} Set — Lightning Comics PDX`);
        this.meta.updateTag({ name: 'description', content: `${comic.title} — set of ${count} comic${count !== 1 ? 's' : ''} available for claim at Lightning Comics PDX in Oregon City, OR.` });
      }
    });

    this.cartService.getClaimedMap().subscribe({ next: m => this.claimedMap = m, error: () => {} });
    if (this.auth.isApproved()) {
      this.cartService.getMyCart().subscribe({ next: cart => this.myCart = cart, error: () => {} });
    }
  }

  get totalPrice(): number {
    return this.setMembers.reduce((sum, m) => sum + (m.salePrice ?? 0), 0);
  }

  get activeLargeImageId(): string | null | undefined {
    return this.activeImage === 'front' ? this.container?.largeCachedImageId : this.container?.largeBackImageId;
  }

  selectImage(which: 'front' | 'back'): void {
    this.activeImage = which;
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

  canClaimSet(): boolean {
    return this.setMembers.length > 0 &&
           this.totalPrice > 0 &&
           !this.isSetInMyCart() &&
           !this.isSetClaimedByOther() &&
           (this.myCart?.status === 'OPEN' || !this.myCart);
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

  onImageFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length || !this.container) return;
    const file = input.files[0];
    this.imageUploading = true;
    this.imageUploadError = '';
    this.imageService.uploadComicImage(this.container.id, file).subscribe({
      next: (updatedComic: Comic) => {
        this.imageUploading = false;
        if (this.container) {
          this.container.largeCachedImageId = updatedComic.largeCachedImageId;
          this.container.smallCachedImageId = updatedComic.smallCachedImageId;
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
    if (!input.files?.length || !this.container) return;
    const file = input.files[0];
    this.backImageUploading = true;
    this.backImageUploadError = '';
    this.imageService.uploadComicBackImage(this.container.id, file).subscribe({
      next: (updatedComic: Comic) => {
        this.backImageUploading = false;
        if (this.container) {
          this.container.largeBackImageId = updatedComic.largeBackImageId;
          this.container.smallBackImageId = updatedComic.smallBackImageId;
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
