import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, firstValueFrom } from 'rxjs';
import { of } from 'rxjs';

export interface GradeOption {
  value: number;
  label: string;
}

export interface ComicEnums {
  coverVariants: string[];
  gradingCompanies: string[];
  grades: GradeOption[];
  pageQualities: string[];
}

export interface AppConfig extends ComicEnums {
  emailEnabled: boolean;
  awardModeEnabled: boolean;
  finalizeHours: number;
}

const DEFAULT_CONFIG: AppConfig = {
  emailEnabled: true,
  awardModeEnabled: true,
  finalizeHours: 20,
  coverVariants: [],
  gradingCompanies: [],
  grades: [],
  pageQualities: []
};

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

  get emailEnabled(): boolean {
    return this._config.emailEnabled;
  }

  get awardModeEnabled(): boolean {
    return this._config.awardModeEnabled;
  }

  get finalizeHours(): number {
    return this._config.finalizeHours ?? 20;
  }

  getEnums(): ComicEnums {
    return {
      coverVariants: this._config.coverVariants,
      gradingCompanies: this._config.gradingCompanies,
      grades: this._config.grades,
      pageQualities: this._config.pageQualities
    };
  }
}
