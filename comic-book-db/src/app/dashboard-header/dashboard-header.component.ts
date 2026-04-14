import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
    selector: 'app-dashboard-header',
    templateUrl: './dashboard-header.component.html',
    styleUrls: ['./dashboard-header.component.css'],
    standalone: false
})
export class DashboardHeaderComponent {
  @Input() itemCount: number = 0;
  @Input() totalCount: number = 0;
  @Input() currentPage: number = 1;
  @Input() totalPages: number = 0;

  get isFiltered(): boolean {
    return this.excludeClaimed || this.showPricedOnly || this.totalPages > 1;
  }
  @Input() excludeClaimed: boolean = false;
  @Output() excludeClaimedChange = new EventEmitter<boolean>();
  @Input() showPricedOnly: boolean = false;
  @Output() showPricedOnlyChange = new EventEmitter<boolean>();
  @Input() biddableOnly: boolean = false;
  @Output() biddableOnlyChange = new EventEmitter<boolean>();
  @Input() sortOrder: string = 'oldest-first';
  @Output() sortOrderChange = new EventEmitter<string>();
  @Output() goToPage = new EventEmitter<number>();
}
