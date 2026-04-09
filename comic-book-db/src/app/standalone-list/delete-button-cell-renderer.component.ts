import { Component } from '@angular/core';
import { ICellRendererAngularComp } from 'ag-grid-angular';
import { ICellRendererParams } from 'ag-grid-community';
import { ComicService } from '../comic.service';

@Component({
  selector: 'app-delete-button-cell-renderer',
  standalone: true,
  template: `
    <button class="del-btn" (click)="onDelete()" title="Delete comic" [disabled]="deleting">
      {{ deleting ? '…' : '🗑' }}
    </button>
  `,
  styles: [`
    .del-btn { background: none; border: none; cursor: pointer; font-size: 15px;
               padding: 0 4px; line-height: 1; color: #c0392b; }
    .del-btn:hover:not(:disabled) { color: #e74c3c; }
    .del-btn:disabled { opacity: 0.4; cursor: not-allowed; }
  `]
})
export class DeleteButtonCellRendererComponent implements ICellRendererAngularComp {

  private params!: ICellRendererParams;
  deleting = false;

  constructor(private comicService: ComicService) {}

  agInit(params: ICellRendererParams): void {
    this.params = params;
  }

  refresh(params: ICellRendererParams): boolean {
    this.params = params;
    return true;
  }

  onDelete(): void {
    const data = this.params.data;
    if (!data) return;
    if (!confirm(`Delete "${data.title || 'this comic'}"?\n\nThis cannot be undone.`)) return;

    this.deleting = true;
    this.comicService.deleteComic(data.id).subscribe({
      next: () => {
        this.params.api.applyTransaction({ remove: [data] });
      },
      error: () => {
        this.deleting = false;
        alert('Delete failed. Please try again.');
      }
    });
  }
}
