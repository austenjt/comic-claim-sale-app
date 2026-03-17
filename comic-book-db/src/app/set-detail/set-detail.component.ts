import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { Observable, of } from 'rxjs';

import { Comic } from '../comic';
import { Cart } from '../cart';
import { ComicService } from '../comic.service';
import { ImageService } from '../image.service';
import { CartService } from '../cart.service';
import { ToastService } from '../toast.service';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-set-detail',
  templateUrl: './set-detail.component.html',
  styleUrls: ['./set-detail.component.css']
})
export class SetDetailComponent implements OnInit {

  container: Comic | undefined;
  setMembers: Comic[] = [];
  claimedMap: Record<string, string> = {};
  myCart: Cart | null = null;
  claimError = '';
  actionLoading = false;
  loading = true;
  imageUploading = false;
  imageUploadError = '';
  backImageUploading = false;
  backImageUploadError = '';

  constructor(
    private route: ActivatedRoute,
    private comicService: ComicService,
    private imageService: ImageService,
    private cartService: CartService,
    private toastService: ToastService,
    public auth: AuthService,
    private location: Location
  ) {}

  ngOnInit(): void {
    const id = parseInt(this.route.snapshot.paramMap.get('id')!, 10);

    this.comicService.getComic(id).subscribe(comic => {
      this.container = comic;
      if (comic?.collectionGroup != null) {
        this.comicService.getRemoteComics().subscribe({
          next: all => {
            this.setMembers = all.filter(c => c.collectionGroup === comic.collectionGroup);
            this.loading = false;
          },
          error: () => { this.loading = false; }
        });
      } else {
        this.loading = false;
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

  get displayMembers(): Comic[] {
    return this.setMembers.filter(m => !m.isContainer && !(m as any).container);
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
    return this.setMembers.some(m => this.isInMyCart(m.id));
  }

  isSetClaimedByOther(): boolean {
    return this.setMembers.some(m =>
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
        for (const m of this.setMembers) {
          this.claimedMap[String(m.id)] = new Date().toISOString();
        }
        this.actionLoading = false;
        this.toastService.show(`"${this.container!.title}" set (${this.setMembers.length} books) added to your cart.`);
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

  getImageURL(imageName: string | null | undefined): Observable<string> {
    if (!imageName) return of('assets/comic-book-large.png');
    return of(this.imageService.getRemoteImageURLByName(imageName));
  }

  getMemberImageURL(imageName: string | null | undefined): Observable<string> {
    if (!imageName) return of('assets/comic-book-small.png');
    return of(this.imageService.getRemoteImageURLByName(imageName));
  }
}
