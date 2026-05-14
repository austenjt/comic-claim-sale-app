import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';

import { Observable, of, from } from 'rxjs';
import { catchError, map, tap, switchMap } from 'rxjs/operators';

import { Comic, PagedResponse } from './comic';
import { MessageService } from './message.service';

export interface CsvUploadResult {
  succeeded: any[];
  failed: any[];
  duplicates: any[];
  errors?: string[];
}

@Injectable({ providedIn: 'root' })
export class ComicService {

  private baseServiceUrl = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';
  //private baseServiceUrl = 'http://localhost:7071/api';
  private comicsUrl = this.baseServiceUrl + '/comics';
  private setsUrl = this.baseServiceUrl + '/sets';
  private dataLoadUrl = this.comicsUrl + '/data';
  private searchUrl = this.baseServiceUrl + '/search';
  private syncUrl = this.comicsUrl + '/data/sync'

  httpOptions = {
    headers: new HttpHeaders({ 'Content-Type': 'application/json' })
  };

  constructor(private http: HttpClient, private messageService: MessageService) {
      this.log('Loading comic service...');
  }

  /** Flattens a nested comics response into a flat list that includes set members. */
  private flattenComics(nestedComics: Comic[]): Comic[] {
    const result: Comic[] = [];
    for (const comic of nestedComics) {
      const { items, ...rest } = comic as any;
      result.push(rest as Comic);
      if (items && items.length > 0) {
        result.push(...items);
      }
    }
    return result;
  }

  /** GET a single page of for-sale comics from the server. Errors propagate for fallback handling. */
  getDashboardPage(
    pageNumber: number,
    sort: string,
    onlyPriced: boolean
  ): Observable<PagedResponse<Comic>> {
    const params = new HttpParams()
      .set('pageNumber', pageNumber.toString())
      .set('sort', sort)
      .set('onlyPriced', onlyPriced.toString());
    return this.http.get<PagedResponse<Comic>>(this.comicsUrl, { params }).pipe(
      tap(() => this.log(`fetched dashboard page ${pageNumber} (sort=${sort}, onlyPriced=${onlyPriced})`))
    );
  }

  getSeriesList(): Observable<string[]> {
    return this.http.get<string[]>(`${this.comicsUrl}/series`).pipe(
      catchError(this.handleError<string[]>('getSeriesList', []))
    );
  }

  syncImages(): Observable<string[]> {
      return this.http.get<string[]>(this.syncUrl)
        .pipe(
          tap(response => {
            this.log('synced comic images' + response);
          }),
          catchError(this.handleError('syncImages', []))
        );
  }


  /** GET all SET-type comics (admin use only). */
  getSets(): Observable<Comic[]> {
    return this.http.get<Comic[]>(this.setsUrl).pipe(
      tap(() => this.log('fetched sets')),
      catchError(this.handleError<Comic[]>('getSets', []))
    );
  }

  getNextSetGroupId(): Observable<number> {
    return this.http.get<{ nextGroupId: number }>(`${this.setsUrl}/next-group-id`).pipe(
      tap(() => this.log('fetched next set group id')),
      map(r => r.nextGroupId),
      catchError(this.handleError<number>('getNextSetGroupId', 1))
    );
  }

  /** GET standalone comics not belonging to any set (admin use only). */
  getSingleComics(): Observable<Comic[]> {
    return this.http.get<Comic[]>(`${this.comicsUrl}/single`).pipe(
      tap(() => this.log('fetched single comics')),
      catchError(this.handleError<Comic[]>('getSingleComics', []))
    );
  }

  /** GET comics from the server as a flat list (set members included at top level). */
  getRemoteComics(): Observable<Comic[]> {
    return this.http.get<Comic[]>(this.comicsUrl)
      .pipe(
        map(nested => this.flattenComics(nested)),
        tap(_ => this.log('fetched comics from remote')),
        catchError(this.handleError<Comic[]>('getRemoteComics', []))
      );
  }

  /** GET comic by id. Return `undefined` when id not found */
  getComicNo404<Data>(id: number): Observable<Comic> {
    const url = `${this.comicsUrl}/?id=${id}`;
    return this.http.get<Comic[]>(url)
      .pipe(
        map(comics => comics[0]), // returns a {0|1} element array
        tap(h => {
          const outcome = h ? 'fetched' : 'did not find';
          this.log(`${outcome} comic id=${id}`);
        }),
        catchError(this.handleError<Comic>(`getComic id=${id}`))
      );
  }

  /** GET comic by id. Will 404 if id not found */
  getComic(id: number): Observable<Comic> {
    const url = `${this.comicsUrl}/${id}`;
    return this.http.get<Comic>(url, { params: { _t: Date.now().toString() } }).pipe(
      tap(_ => this.log(`fetched comic id=${id}`)),
      catchError(this.handleError<Comic>(`getComic id=${id}`))
    );
  }

  /* GET comics whose name contains search term */
  searchComics(term: string): Observable<Comic[]> {
    if (!term.trim()) {
      return of([]);
    }
    return this.http.get<Comic[]>(`${this.searchUrl}?title=${term}`).pipe(
      tap(x => x.length ?
         this.log(`found comics matching "${term}"`) :
         this.log(`no comics matching "${term}"`)),
      catchError(this.handleError<Comic[]>('searchComics', []))
    );
  }

  //////// Save methods //////////

  /** POST: add a new comic to the server */
  addComic(comic: Comic): Observable<Comic> {
    return this.http.post<Comic>(this.comicsUrl, comic, this.httpOptions).pipe(
      tap((newComic: Comic) => {
        this.log(`added comic w/ id=${newComic.id} and title=${newComic.title}`);
      }),
      catchError(this.handleError<Comic>('addComic'))
    );
  }

  /** DELETE: delete the set container and clear collectionGroup from all members */
  deleteSet(collectionGroup: number): Observable<void> {
    return this.http.delete<void>(`${this.baseServiceUrl}/sets/${collectionGroup}`, { responseType: 'text' as 'json' }).pipe(
      tap(_ => this.log(`deleted set with collectionGroup=${collectionGroup}`)),
      catchError(this.handleError<void>('deleteSet'))
    );
  }

  /** DELETE: permanently delete the set container AND all member comics */
  deleteSetFully(collectionGroup: number): Observable<void> {
    return this.http.delete<void>(`${this.baseServiceUrl}/sets/${collectionGroup}/full`, { responseType: 'text' as 'json' }).pipe(
      tap(_ => this.log(`fully deleted set with collectionGroup=${collectionGroup}`)),
      catchError(this.handleError<void>('deleteSetFully'))
    );
  }

  /** DELETE: delete the comic from the server */
  deleteComic(id: number): Observable<Comic> {
    const url = `${this.comicsUrl}/${id}`;
    return this.http.delete<Comic>(url, this.httpOptions).pipe(
      tap(_ => {
        this.log(`deleted comic w/ id=${id}`);
      }),
      catchError(this.handleError<Comic>('deleteComic'))
    );
  }

  /** POST: increment the view count for a comic. Best-effort — errors are swallowed. */
  recordView(id: number): Observable<{ viewCount: number }> {
    return this.http.post<{ viewCount: number }>(`${this.comicsUrl}/${id}/view`, null).pipe(
      catchError(this.handleError<{ viewCount: number }>('recordView', { viewCount: 0 }))
    );
  }

  /** PUT: update the comic on the server */
  updateComic(comic: Comic): Observable<any> {
    this.log(`received update for comic id=${comic.id} and title=${comic.title}`);
    return this.http.put(this.comicsUrl, comic, this.httpOptions).pipe(
      tap(_ => {
        this.log(`updated comic id=${comic.id} and title=${comic.title}`);
      }),
      catchError(this.handleError<any>('updateComic'))
    );
  }

  /**
   * Reads a CSV file and POSTs its text content to the data load endpoint.
   * Returns an Observable so callers can track progress and handle errors.
   */
  public uploadCSVFile(csvFile: File, collectionGroup?: number, setPriceToPricePaid = false, markForSale = true): Observable<CsvUploadResult> {
    const readFile = new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = () => reject(reader.error);
      reader.readAsText(csvFile);
    });

    const params: string[] = [];
    if (collectionGroup != null) params.push(`collectionGroup=${collectionGroup}`);
    if (setPriceToPricePaid) params.push(`setPriceToPricePaid=true`);
    if (!markForSale) params.push(`markForSale=false`);
    const url = params.length > 0 ? `${this.dataLoadUrl}?${params.join('&')}` : this.dataLoadUrl;
    const headers = new HttpHeaders({ 'Content-Type': 'text/plain' });
    const empty: CsvUploadResult = { succeeded: [], failed: [], duplicates: [] };
    return from(readFile).pipe(
      switchMap(csvContent => this.http.post<CsvUploadResult>(url, csvContent, { headers })),
      tap(() => this.log('CSV uploaded successfully')),
      catchError(this.handleError('uploadCSVFile', empty))
    );
  }

  /**
   * Handle Http operation that failed.
   * Let the app continue.
   *
   * @param operation - name of the operation that failed
   * @param result - optional value to return as the observable result
   */
  private handleError<T>(operation = 'operation', result?: T) {
    return (error: any): Observable<T> => {
      console.error(error);
      this.log(`${operation} failed: ${error.message}`);
      return of(result as T);
    };
  }

  /** Log a ComicService message with the MessageService */
  private log(message: string) {
    this.messageService.add(`ComicService: ${message}`);
  }

}

