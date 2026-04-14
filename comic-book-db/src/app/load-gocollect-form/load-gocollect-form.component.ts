import { Component } from '@angular/core';
import { ComicService, CsvUploadResult } from '../comic.service';
import { Comic } from '../comic';

@Component({
    selector: 'app-load-gocollect-form',
    templateUrl: './load-gocollect-form.component.html',
    styleUrls: ['./load-gocollect-form.component.css'],
    standalone: false
})
export class LoadGoCollectFormComponent {

  fileError = false;
  selectedFile?: File;
  status: 'idle' | 'processing' | 'success' | 'error' = 'idle';
  statusMessage = '';

  importAsSet = false;
  setPriceToPricePaid = false;
  availableSets: Comic[] = [];
  selectedSet: Comic | null = null;

  constructor(private comicService: ComicService) {}

  onFileChange(event: any) {
    const file = event.target.files[0];
    this.status = 'idle';
    this.statusMessage = '';
    if (file && this.validateCSV(file)) {
      this.fileError = false;
      this.selectedFile = file;
    } else {
      this.fileError = true;
      this.selectedFile = undefined;
    }
  }

  onImportAsSetChange(): void {
    if (this.importAsSet && this.availableSets.length === 0) {
      this.comicService.getSets().subscribe(sets => {
        this.availableSets = sets;
      });
    }
    if (!this.importAsSet) {
      this.selectedSet = null;
    }
  }

  get canProcess(): boolean {
    return !!this.selectedFile && (!this.importAsSet || this.selectedSet != null);
  }

  processFile() {
    if (!this.canProcess) return;
    this.status = 'processing';
    this.statusMessage = '';
    const collectionGroup = this.importAsSet && this.selectedSet?.collectionGroup != null
      ? this.selectedSet.collectionGroup
      : undefined;
    this.comicService.uploadCSVFile(this.selectedFile!, collectionGroup, this.setPriceToPricePaid).subscribe({
      next: (result: CsvUploadResult) => {
        this.status = result.errors?.length ? 'error' : 'success';
        this.statusMessage =
          `${result.succeeded.length} added` +
          (collectionGroup != null ? ` to Set ${collectionGroup}` : '') +
          `, ${result.duplicates.length} duplicate${result.duplicates.length !== 1 ? 's' : ''}, ` +
          `${result.failed.length} failed.` +
          (result.errors?.length ? ` Reason: ${result.errors[0]}` : '');
        this.selectedFile = undefined;
      },
      error: () => {
        this.status = 'error';
        this.statusMessage = 'Processing failed. Please try again.';
      }
    });
  }

  private validateCSV(file: File): boolean {
    return file.type === 'text/csv' || (file.name.endsWith('.csv') && !file.type);
  }
}
