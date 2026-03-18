import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ICellEditorAngularComp } from 'ag-grid-angular';
import { ICellEditorParams } from 'ag-grid-community';
import { Subject, Subscription, EMPTY } from 'rxjs';
import { debounceTime, switchMap } from 'rxjs/operators';
import { ValidationService, NumberValidationResult } from '../validation.service';

@Component({
  selector: 'app-number-cell-editor',
  standalone: true,
  imports: [FormsModule, CommonModule],
  template: `
    <div class="num-editor-wrap">
      <input #inputEl
             class="num-editor-input"
             [class.num-editor-invalid]="!!error"
             type="text"
             [(ngModel)]="value"
             (ngModelChange)="onValueChange()"
             (keydown)="onKeyDown($event)">
      <div class="num-editor-validating" *ngIf="validating">Checking…</div>
      <div class="num-editor-error"   *ngIf="!validating && error">{{ error }}</div>
      <div class="num-editor-hint"    *ngIf="!validating && !error">{{ hint }}</div>
    </div>
  `,
  styles: [`
    .num-editor-wrap {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 8px;
      background: white;
      border: 1px solid #ccc;
      border-radius: 4px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      min-width: 200px;
    }
    .num-editor-input {
      border: 1px solid #ccc;
      border-radius: 3px;
      padding: 5px 8px;
      font-size: 13px;
      outline: none;
      width: 100%;
      box-sizing: border-box;
    }
    .num-editor-input:focus { border-color: #1976d2; }
    .num-editor-invalid     { border-color: #d32f2f !important; background-color: #fff5f5; }
    .num-editor-validating  { color: #888; font-size: 11px; font-style: italic; }
    .num-editor-error       { color: #d32f2f; font-size: 11px; }
    .num-editor-hint        { color: #888; font-size: 11px; }
  `]
})
export class NumberCellEditorComponent implements ICellEditorAngularComp, OnInit, OnDestroy {

  @ViewChild('inputEl') inputEl!: ElementRef<HTMLInputElement>;

  value = '';
  error = '';
  validating = false;
  hint = 'Whole number ≥ 0, or: -1, NN, SET';

  private lastResult: NumberValidationResult | null = null;
  private inputSubject = new Subject<string>();
  private sub!: Subscription;

  constructor(private validationService: ValidationService) {}

  ngOnInit(): void {
    this.sub = this.inputSubject.pipe(
      debounceTime(300),
      switchMap(v => {
        if (!v.trim()) {
          this.validating = false;
          this.error = 'Value is required.';
          this.lastResult = null;
          return EMPTY;
        }
        this.validating = true;
        return this.validationService.validateComicNumber(v);
      })
    ).subscribe({
      next: (result: NumberValidationResult) => {
        this.validating = false;
        this.lastResult = result;
        if (result.valid) {
          this.error = '';
          this.hint = result.asSentinel
            ? `Sentinel: ${result.asSentinel}`
            : `Issue #${result.asNumber}`;
        } else {
          this.error = result.message ?? 'Invalid value.';
        }
      },
      error: () => {
        this.validating = false;
        this.error = 'Validation service unavailable.';
        this.lastResult = null;
      }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  agInit(params: ICellEditorParams): void {
    this.value = params.value != null ? String(params.value) : '';
    // Don't validate the initial value — it came from the grid and is assumed valid.
    // hint stays as the default until the user types something.
  }

  afterGuiAttached(): void {
    setTimeout(() => {
      this.inputEl?.nativeElement.focus();
      this.inputEl?.nativeElement.select();
    });
  }

  getValue(): string {
    return this.value.trim();
  }

  isCancelAfterEnd(): boolean {
    // Block commit if still validating or if the last result was invalid
    return this.validating || !!this.error;
  }

  onValueChange(): void {
    this.error = '';
    this.validating = !!this.value.trim();
    this.inputSubject.next(this.value);
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Escape') return;
    if ((event.key === 'Tab' || event.key === 'Enter') && (this.error || this.validating)) {
      event.stopPropagation();
    }
  }
}
