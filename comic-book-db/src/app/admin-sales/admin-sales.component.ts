import { Component, OnInit } from '@angular/core';
import { DiscountService } from '../discount.service';
import { Discount, DiscountType } from '../discount';
import { AuthService } from '../auth.service';
import { SalesModalService } from '../sales-modal.service';

@Component({
    selector: 'app-admin-sales',
    templateUrl: './admin-sales.component.html',
    styleUrls: ['./admin-sales.component.css'],
    standalone: false
})
export class AdminSalesComponent implements OnInit {
  discounts: Discount[] = [];
  loading = false;
  error = '';

  showForm = false;
  editingId: string | null = null;

  formName = '';
  formType: DiscountType = 'RAW_PERCENTAGE';
  formActive = true;
  formPercentageOff = 0;
  formXBooks = 1;
  formExcludeSets = false;
  formExcludeGraded = false;

  readonly typeOptions: { value: DiscountType; label: string }[] = [
    { value: 'RAW_PERCENTAGE', label: 'Flat % Off' },
    { value: 'BUY_X_GET_ONE_FREE', label: 'Buy X Get One Free' },
    { value: 'PERCENT_OFF_OVER_X_BOOKS', label: '% Off Over X Books' }
  ];

  constructor(
    private discountService: DiscountService,
    public auth: AuthService,
    private salesModal: SalesModalService
  ) {}

  ngOnInit() {
    this.load();
  }

  previewModal(): void { this.salesModal.show(); }

  load() {
    this.loading = true;
    this.discountService.getAll().subscribe({
      next: d => { this.discounts = d; this.loading = false; },
      error: () => { this.error = 'Failed to load discounts.'; this.loading = false; }
    });
  }

  openCreate() {
    this.editingId = null;
    this.formName = '';
    this.formType = 'RAW_PERCENTAGE';
    this.formActive = true;
    this.formPercentageOff = 0;
    this.formXBooks = 1;
    this.formExcludeSets = false;
    this.formExcludeGraded = false;
    this.showForm = true;
  }

  openEdit(d: Discount) {
    this.editingId = d.id;
    this.formName = d.name;
    this.formType = d.type;
    this.formActive = d.isActive;
    this.formPercentageOff = d.percentageOff;
    this.formXBooks = d.xBooks || 1;
    this.formExcludeSets = d.excludeSets ?? false;
    this.formExcludeGraded = d.excludeGraded ?? false;
    this.showForm = true;
  }

  cancelForm() {
    this.showForm = false;
    this.editingId = null;
  }

  save() {
    const payload: Partial<Discount> = {
      name: this.formName,
      type: this.formType,
      isActive: this.formActive,
      percentageOff: this.formPercentageOff,
      xBooks: Number(this.formXBooks) || 1,
      excludeSets: this.formExcludeSets,
      excludeGraded: this.formExcludeGraded
    };

    if (this.editingId) {
      this.discountService.update({ ...payload, id: this.editingId } as Discount).subscribe({
        next: () => { this.showForm = false; this.load(); },
        error: () => this.error = 'Failed to save discount.'
      });
    } else {
      this.discountService.create(payload).subscribe({
        next: () => { this.showForm = false; this.load(); },
        error: () => this.error = 'Failed to create discount.'
      });
    }
  }

  delete(d: Discount) {
    if (!confirm(`Delete discount "${d.name}"?`)) return;
    this.discountService.delete(d.id).subscribe({
      next: () => this.load(),
      error: () => this.error = 'Failed to delete discount.'
    });
  }

  get rulePreview(): string {
    switch (this.formType) {
      case 'RAW_PERCENTAGE':
        return `${this.formPercentageOff}% off the cart total.`;
      case 'BUY_X_GET_ONE_FREE':
        return `Every ${(this.formXBooks || 1) + 1} books: cheapest book is free.`;
      case 'PERCENT_OFF_OVER_X_BOOKS':
        return `${this.formPercentageOff}% off when cart has more than ${this.formXBooks} book${this.formXBooks === 1 ? '' : 's'}.`;
    }
  }

  needsPercentage(): boolean {
    return this.formType === 'RAW_PERCENTAGE' || this.formType === 'PERCENT_OFF_OVER_X_BOOKS';
  }

  needsXBooks(): boolean {
    return this.formType === 'BUY_X_GET_ONE_FREE' || this.formType === 'PERCENT_OFF_OVER_X_BOOKS';
  }

  describeDiscount(d: Discount): string {
    const note = this.exclusionNote(d);
    switch (d.type) {
      case 'RAW_PERCENTAGE':
        return `${d.percentageOff}% off${note}`;
      case 'BUY_X_GET_ONE_FREE':
        return `Buy ${d.xBooks || '?'} get 1 free${note}`;
      case 'PERCENT_OFF_OVER_X_BOOKS':
        return `${d.percentageOff}% off over ${d.xBooks || '?'} books${note}`;
    }
  }

  /** Concise admin-facing summary of which categories a rule excludes. */
  private exclusionNote(d: Discount): string {
    const parts: string[] = [];
    if (d.excludeSets) parts.push('sets');
    if (d.excludeGraded) parts.push('graded');
    return parts.length ? ` (${parts.join(', ')} excluded)` : '';
  }
}
