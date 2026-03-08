import { Component } from '@angular/core';
import { ComicService, CsvUploadResult } from '../comic.service';

@Component({
  selector: 'app-load-gocollect-form',
  templateUrl: './load-gocollect-form.component.html',
  styleUrls: ['./load-gocollect-form.component.css']
})
export class LoadGoCollectFormComponent {

  fileError = false;
  selectedFile?: File;
  status: 'idle' | 'processing' | 'success' | 'error' = 'idle';
  statusMessage = '';

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

  processFile() {
    if (!this.selectedFile) return;
    this.status = 'processing';
    this.statusMessage = '';
    this.comicService.uploadCSVFile(this.selectedFile).subscribe({
      next: (result: CsvUploadResult) => {
        this.status = 'success';
        this.statusMessage =
          `${result.succeeded.length} added, ` +
          `${result.duplicates.length} duplicate${result.duplicates.length !== 1 ? 's' : ''}, ` +
          `${result.failed.length} failed.`;
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
