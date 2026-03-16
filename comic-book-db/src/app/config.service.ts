import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, firstValueFrom } from 'rxjs';
import { of } from 'rxjs';

export interface AppConfig {
  gmailEnabled: boolean;
}

const DEFAULT_CONFIG: AppConfig = { gmailEnabled: true };

@Injectable({ providedIn: 'root' })
export class ConfigService {

  private readonly apiBase = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';
  // private readonly apiBase = 'http://localhost:7071/api';

  private _config: AppConfig = DEFAULT_CONFIG;

  constructor(private http: HttpClient) {}

  load(): Promise<void> {
    return firstValueFrom(
      this.http.get<AppConfig>(`${this.apiBase}/config`).pipe(
        catchError(() => of(DEFAULT_CONFIG))
      )
    ).then(config => { this._config = config; });
  }

  get gmailEnabled(): boolean {
    return this._config.gmailEnabled;
  }
}
