import { Injectable } from '@angular/core';

const AWARD_MODE_KEY = 'adminAwardModeEnabled';

@Injectable({ providedIn: 'root' })
export class AdminSettingsService {

  get awardModeEnabled(): boolean {
    return localStorage.getItem(AWARD_MODE_KEY) === 'true';
  }

  set awardModeEnabled(value: boolean) {
    localStorage.setItem(AWARD_MODE_KEY, String(value));
  }
}
