import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class ImageService {

  private baseServiceUrl = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';
  //private baseServiceUrl = 'http://localhost:7071/api';
  private imagesAPIUrl = this.baseServiceUrl + '/images';

  constructor(private http: HttpClient) {}

  getImagesBaseURL() {
      return this.imagesAPIUrl;
  }

  getRemoteImageNames(): Observable<string[]> {
    return this.http.get<string[]>(this.imagesAPIUrl)
      .pipe(
        catchError(this.handleError<string[]>([]))
      );
  }

  getRemoteImageByName(imageName: string): Observable<Blob | null> {
    return this.http.get(this.imagesAPIUrl + "/" + imageName, { responseType: 'blob' })
      .pipe(
        catchError(() => of(null))
      );
  }

  // fastest way to get an image, using static url
  getRemoteImageURLByName(imageName: string): string {
     return this.imagesAPIUrl + "/" + imageName;
  }

  uploadComicImage(comicId: number, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post(`${this.baseServiceUrl}/comics/${comicId}/image`, formData);
  }

  uploadComicBackImage(comicId: number, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post(`${this.baseServiceUrl}/comics/${comicId}/back-image`, formData);
  }

  uploadTradeFrontImage(comicId: number, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post(`${this.baseServiceUrl}/comics/${comicId}/trade-image`, formData);
  }

  uploadTradeBackImage(comicId: number, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post(`${this.baseServiceUrl}/comics/${comicId}/trade-back-image`, formData);
  }

  createImage(imageName: string, image: Blob): Observable<any> {
    const blobFile = new File([image], imageName, { type: image.type });
    const formData = new FormData();
    formData.append('file', blobFile, imageName);
    const urlWithForce = `${this.imagesAPIUrl}/${imageName}`;
    return this.http.post(urlWithForce, formData, { responseType: 'text' })
      .pipe(
        catchError(this.handleError([]))
      );
  }

  updateImage(imageName: string, image: ArrayBuffer, force: Boolean): Observable<any> {
    const imageFile = new File([image], imageName, { type: 'image/jpeg' });
    const formData = new FormData();
    formData.append('file', imageFile, imageName);
    const urlWithForce = `${this.imagesAPIUrl}/${imageName}?force=${force}`;
    return this.http.put(urlWithForce, formData, { responseType: 'text' })
      .pipe(
        catchError(this.handleError([]))
      );
  }

  private handleError<T>(result?: T) {
    return (error: any): Observable<T> => {
      console.error(error);
      return of(result as T);
    };
  }

}
