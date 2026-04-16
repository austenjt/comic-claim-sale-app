import { Injectable } from '@angular/core';
import { Comic } from './comic';

export interface NavItem {
  id: number;
  docType: string | null;
}

@Injectable({ providedIn: 'root' })
export class DashboardNavService {
  private navList: NavItem[] = [];

  setList(comics: Comic[]): void {
    this.navList = comics.map(c => ({ id: c.id, docType: c.docType ?? null }));
  }

  /** Returns prev/next items with circular wrapping. Both are null only when the list is empty or the id is not found. */
  getAdjacent(currentId: number): { prev: NavItem | null; next: NavItem | null } {
    if (this.navList.length === 0) return { prev: null, next: null };
    const idx = this.navList.findIndex(c => c.id === currentId);
    if (idx < 0) return { prev: null, next: null };
    return {
      prev: this.navList[(idx - 1 + this.navList.length) % this.navList.length],
      next: this.navList[(idx + 1) % this.navList.length],
    };
  }

  /** Returns 1-based position and total count. index is 0 when id is not in the list. */
  getPosition(currentId: number): { index: number; total: number } {
    const idx = this.navList.findIndex(c => c.id === currentId);
    return {
      index: idx >= 0 ? idx + 1 : 0,
      total: this.navList.length,
    };
  }
}
