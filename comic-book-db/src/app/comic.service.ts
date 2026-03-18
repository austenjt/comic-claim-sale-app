import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

import { Observable, of, from } from 'rxjs';
import { catchError, map, tap, switchMap } from 'rxjs/operators';

import { Comic } from './comic';
import { MessageService } from './message.service';
import { BehaviorSubject } from 'rxjs';

export interface CsvUploadResult {
  succeeded: any[];
  failed: any[];
  duplicates: any[];
}

@Injectable({ providedIn: 'root' })
export class ComicService {

  private baseServiceUrl = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';
  //private baseServiceUrl = 'http://localhost:7071/api';
  private comicsUrl = this.baseServiceUrl + '/comics';
  private dataLoadUrl = this.comicsUrl + '/data';
  private searchUrl = this.baseServiceUrl + '/search';
  private syncUrl = this.comicsUrl + '/data/sync'

  private comicsSubject = new BehaviorSubject<Comic[]>([]);        // flat list (all comics including set members)
  private nestedComicsSubject = new BehaviorSubject<Comic[]>([]); // top-level list (sets with items embedded)

  httpOptions = {
    headers: new HttpHeaders({ 'Content-Type': 'application/json' })
  };

  constructor(private http: HttpClient, private messageService: MessageService) {
      this.log('Loading comic service.  Please wait for database warmup...');
      this.loadInitialData();
  }

  private loadInitialData() {
    this.http.get<Comic[]>(this.comicsUrl)
        .subscribe(nested => {
          this.nestedComicsSubject.next(nested);
          this.comicsSubject.next(this.flattenComics(nested));
        });
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

  syncImages(): Observable<string[]> {
      return this.http.get<string[]>(this.syncUrl)
        .pipe(
          tap(response => {
            this.log('synced comic images' + response);
          }),
          catchError(this.handleError('syncImages', []))
        );
  }


  /** Returns a flat cached list of all comics including set members (used by admin grid). */
  getCachedComics(): Observable<Comic[]> {
    this.log('fetched comics from memory cache')
    return this.comicsSubject.asObservable();
  }

  /** Returns the top-level nested cached list: sets have items[] populated, members excluded from top level. */
  getCachedNestedComics(): Observable<Comic[]> {
    return this.nestedComicsSubject.asObservable();
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

  /** GET comics from the server in nested form: sets include items[], members excluded from top level. */
  getRemoteNestedComics(): Observable<Comic[]> {
    return this.http.get<Comic[]>(this.comicsUrl)
      .pipe(
        tap(_ => this.log('fetched nested comics from remote')),
        catchError(this.handleError<Comic[]>('getRemoteNestedComics', []))
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
    return this.http.get<Comic>(url).pipe(
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
        this.refreshComics();
      }),
      catchError(this.handleError<Comic>('addComic'))
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

  refreshComics() {
    console.log('Refreshing comic list.');
    this.http.get<Comic[]>(this.comicsUrl).subscribe(nested => {
      this.nestedComicsSubject.next(nested);
      this.comicsSubject.next(this.flattenComics(nested));
    });
  }

  /**
   * Reads a CSV file and POSTs its text content to the data load endpoint.
   * Returns an Observable so callers can track progress and handle errors.
   */
  public uploadCSVFile(csvFile: File): Observable<CsvUploadResult> {
    const readFile = new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = () => reject(reader.error);
      reader.readAsText(csvFile);
    });

    const headers = new HttpHeaders({ 'Content-Type': 'text/plain' });
    const empty: CsvUploadResult = { succeeded: [], failed: [], duplicates: [] };
    return from(readFile).pipe(
      switchMap(csvContent => this.http.post<CsvUploadResult>(this.dataLoadUrl, csvContent, { headers })),
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

