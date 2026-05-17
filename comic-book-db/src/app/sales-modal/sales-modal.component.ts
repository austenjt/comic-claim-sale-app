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
    if (d.type === 'BUY_X_GET_ONE_FREE' || d.type === 'FREE_SHIPPING_OVER_X_BOOKS') return '🎁';
    return `${d.percentageOff}%`;
  }

  describeDiscount(d: Discount): string {
    const note = this.exclusionPhrase(d);
    switch (d.type) {
      case 'RAW_PERCENTAGE':
        return `${d.percentageOff}% off items in your order${note}`;
      case 'BUY_X_GET_ONE_FREE':
        return `Buy ${d.xBooks} books — get another book free${note}`;
      case 'PERCENT_OFF_OVER_X_BOOKS':
        return `${d.percentageOff}% off when you claim more than ${d.xBooks} book${d.xBooks === 1 ? '' : 's'}${note}`;
      case 'FREE_SHIPPING_OVER_X_BOOKS':
        return `Free shipping on orders with ${d.xBooks} or more books`;
      default:
        return '';
    }
  }

  /**
   * Builds the customer-facing parenthetical that lists which categories the rule excludes.
   * Reads naturally — "(sets not included)", "(graded comics not included)",
   * "(sets and graded comics not included)" — depending on which flags are set.
   * Returns an empty string when no exclusions apply.
   */
  private exclusionPhrase(d: Discount): string {
    const parts: string[] = [];
    if (d.excludeSets) parts.push('sets');
    if (d.excludeGraded) parts.push('graded comics');
    if (parts.length === 0) return '';
    let joined: string;
    if (parts.length === 1) {
      joined = parts[0];
    } else if (parts.length === 2) {
      joined = `${parts[0]} and ${parts[1]}`;
    } else {
      // Oxford comma for three or more.
      joined = `${parts.slice(0, -1).join(', ')}, and ${parts[parts.length - 1]}`;
    }
    return ` (${joined} not included)`;
  }
}
