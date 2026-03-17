import { Component, OnInit } from '@angular/core';
import { DiscountService } from '../discount.service';
import { Discount, DiscountType } from '../discount';
import { CartService } from '../cart.service';

@Component({
  selector: 'app-admin-sales',
  templateUrl: './admin-sales.component.html',
  styleUrls: ['./admin-sales.component.css']
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

  readonly typeOptions: { value: DiscountType; label: string }[] = [
    { value: 'RAW_PERCENTAGE', label: 'Flat % Off' },
    { value: 'BUY_X_GET_ONE_FREE', label: 'Buy X Get One Free' },
    { value: 'PERCENTAGE_PER_X_BOOKS', label: '% Per X Books' }
  ];

  resetConfirmText = '';
  resetInProgress = false;
  resetSuccess = '';
  resetError = '';

  constructor(
    private discountService: DiscountService,
    private cartService: CartService
  ) {}

  ngOnInit() {
    this.load();
  }

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
    this.showForm = true;
  }

  openEdit(d: Discount) {
    this.editingId = d.id;
    this.formName = d.name;
    this.formType = d.type;
    this.formActive = d.isActive;
    this.formPercentageOff = d.percentageOff;
    this.formXBooks = d.xBooks || 1;
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
      xBooks: Number(this.formXBooks) || 1
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
      case 'PERCENTAGE_PER_X_BOOKS':
        return `${this.formPercentageOff}% off for every ${this.formXBooks} books in cart (stacks, capped at 100%).`;
    }
  }

  needsPercentage(): boolean {
    return this.formType === 'RAW_PERCENTAGE' || this.formType === 'PERCENTAGE_PER_X_BOOKS';
  }

  needsXBooks(): boolean {
    return this.formType === 'BUY_X_GET_ONE_FREE' || this.formType === 'PERCENTAGE_PER_X_BOOKS';
  }

  resetDatabase() {
    this.resetInProgress = true;
    this.resetSuccess = '';
    this.resetError = '';
    this.cartService.resetDatabase().subscribe({
      next: () => {
        this.resetSuccess = 'Database reset complete. All comics, images, carts, discounts, and sessions have been cleared.';
        this.resetConfirmText = '';
        this.resetInProgress = false;
        this.load();
      },
      error: () => {
        this.resetError = 'Reset failed. Check the server logs.';
        this.resetInProgress = false;
      }
    });
  }

  describeDiscount(d: Discount): string {
    switch (d.type) {
      case 'RAW_PERCENTAGE':
        return `${d.percentageOff}% off`;
      case 'BUY_X_GET_ONE_FREE':
        return `Buy ${d.xBooks || '?'} get 1 free`;
      case 'PERCENTAGE_PER_X_BOOKS':
        return `${d.percentageOff}% per ${d.xBooks || '?'} books`;
    }
  }
}
