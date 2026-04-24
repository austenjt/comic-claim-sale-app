import { Component, HostListener, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { SalesModalService } from '../sales-modal.service';
import { DiscountService } from '../discount.service';
import { Discount } from '../discount';

@Component({
  selector: 'app-sales-modal',
  templateUrl: './sales-modal.component.html',
  styleUrls: ['./sales-modal.component.css'],
  standalone: false
})
export class SalesModalComponent implements OnInit, OnDestroy {
  visible = false;
  discounts: Discount[] = [];
  loading = false;

  private sub!: Subscription;

  constructor(
    private modalService: SalesModalService,
    private discountService: DiscountService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.sub = this.modalService.visible$.subscribe(v => {
      this.visible = v;
      if (v) this.loadDiscounts();
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private loadDiscounts(): void {
    this.loading = true;
    this.discountService.getAll().subscribe({
      next: d => { this.discounts = d.filter(x => x.isActive); this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  close(): void { this.modalService.hide(); }

  browse(): void {
    this.modalService.hide();
    this.router.navigate(['/dashboard']);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void { if (this.visible) this.close(); }

  iconText(d: Discount): string {
    if (d.type === 'BUY_X_GET_ONE_FREE') return '🎁';
    return `${d.percentageOff}%`;
  }

  describeDiscount(d: Discount): string {
    const setsNote = d.excludeSets ? ' (sets not included)' : '';
    switch (d.type) {
      case 'RAW_PERCENTAGE':
        return `${d.percentageOff}% off your entire order${setsNote}`;
      case 'BUY_X_GET_ONE_FREE':
        return `Buy ${d.xBooks} books — get the cheapest one free${setsNote}`;
      case 'PERCENT_OFF_OVER_X_BOOKS':
        return `${d.percentageOff}% off when you claim more than ${d.xBooks} book${d.xBooks === 1 ? '' : 's'}${setsNote}`;
    }
  }
}
