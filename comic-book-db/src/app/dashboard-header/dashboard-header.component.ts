import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-dashboard-header',
  templateUrl: './dashboard-header.component.html',
  styleUrls: ['./dashboard-header.component.css']
})
export class DashboardHeaderComponent {
  @Input() itemCount: number = 0;
  @Input() totalCount: number = 0;

  get isFiltered(): boolean {
    return this.excludeClaimed || this.showPricedOnly;
  }
  @Input() excludeClaimed: boolean = false;
  @Output() excludeClaimedChange = new EventEmitter<boolean>();
  @Input() showPricedOnly: boolean = false;
  @Output() showPricedOnlyChange = new EventEmitter<boolean>();
  @Input() sortOrder: string = 'date-added';
  @Output() sortOrderChange = new EventEmitter<string>();
}
