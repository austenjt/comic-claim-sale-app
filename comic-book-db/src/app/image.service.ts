import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { MessageService } from './message.service';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class ImageService {

  private baseServiceUrl = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';
  //private baseServiceUrl = 'http://localhost:7071/api';
  private imagesAPIUrl = this.baseServiceUrl + '/images';

  private defaultMissingImage = 'assets/missing.png';

  constructor(private http: HttpClient, private messageService: MessageService) {
      this.log('Loading image service.  Please wait for database warmup...');
      console.log(this.getRemoteImageNames().subscribe(names => {
          console.log('Images available: ', names.length);
      }));
  }

  getImagesBaseURL() {
      return this.imagesAPIUrl;
  }

  getRemoteImageNames(): Observable<string[]> {
    return this.http.get<string[]>(this.imagesAPIUrl)
      .pipe(
        tap(_ => this.log('fetched image names from remote')),
        catchError(this.handleError<string[]>('getRemoteImageNames', []))
      );
  }

  getRemoteImageByName(imageName: string): Observable<Blob | null> {
    return this.http.get(this.imagesAPIUrl + "/" + imageName, { responseType: 'blob' })
      .pipe(
        tap(_ => this.log('fetched image ' + imageName + ' from remote')),
        catchError(error => {
          this.log(`Error fetching image ${imageName} from remote.`);
          return of(null);
        })
      );
  }

  // fastest way to get an image, using static url
  getRemoteImageURLByName(imageName: string): string {
     return this.imagesAPIUrl + "/" + imageName;
  }

  uploadComicImage(comicId: number, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post(`${this.baseServiceUrl}/comics/${comicId}/image`, formData)
      .pipe(
        tap(() => this.log(`Uploaded image for comic ${comicId}`)),
        catchError(this.handleError('uploadComicImage', null))
      );
  }

  uploadComicBackImage(comicId: number, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post(`${this.baseServiceUrl}/comics/${comicId}/back-image`, formData)
      .pipe(
        tap(() => this.log(`Uploaded back image for comic ${comicId}`)),
        catchError(this.handleError('uploadComicBackImage', null))
      );
  }

  createImage(imageName: string, image: Blob): Observable<any> {
    const blobFile = new File([image], imageName, { type: image.type });
    const formData = new FormData();
    formData.append('file', blobFile, imageName);
    const urlWithForce = `${this.imagesAPIUrl}/${imageName}`;
    return this.http.post(urlWithForce, formData, { responseType: 'text' })
      .pipe(
        tap(response => this.log(`API POST response: ${response}`)),
        catchError(this.handleError('uploadImage', []))
      );
  }

  updateImage(imageName: string, image: ArrayBuffer, force: Boolean): Observable<any> {
    const imageFile = new File([image], imageName, { type: 'image/jpeg' });
    const formData = new FormData(); // form data needs to be resolved on receiving end!
    formData.append('file', imageFile, imageName);
    const urlWithForce = `${this.imagesAPIUrl}/${imageName}?force=${force}`;
    return this.http.put(urlWithForce, formData, { responseType: 'text' })
      .pipe(
        tap(response => this.log(`API PUT update image response: ${response}`)),
        catchError(this.handleError('uploadImage', []))
      );
  }

  private handleError<T>(operation = 'operation', result?: T) {
    return (error: any): Observable<T> => {
      console.error(error);
      this.log(`${operation} failed: ${error.message}`);
      return of(result as T);
    };
  }

  private log(message: string) {
    this.messageService.add(`ImageService: ${message}`);
  }

}
