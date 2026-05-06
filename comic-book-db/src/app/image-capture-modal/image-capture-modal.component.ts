import { Component, Input, Output, EventEmitter, ViewChild, ElementRef, OnDestroy, NgZone } from '@angular/core';

@Component({
  selector: 'app-image-capture-modal',
  templateUrl: './image-capture-modal.component.html',
  styleUrls: ['./image-capture-modal.component.css'],
  standalone: false
})
export class ImageCaptureModalComponent implements OnDestroy {

  @Input() title = 'Update Image';
  @Output() fileSelected = new EventEmitter<File>();
  @Output() closed = new EventEmitter<void>();

  @ViewChild('videoEl') videoEl!: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasEl') canvasEl!: ElementRef<HTMLCanvasElement>;
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  stage: 'picker' | 'camera' | 'preview' = 'picker';
  previewDataUrl: string | null = null;
  capturedBlob: Blob | null = null;
  cameraError = '';

  private stream: MediaStream | null = null;

  constructor(private zone: NgZone) {}

  openFileInput(): void {
    this.fileInput.nativeElement.click();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    this.fileSelected.emit(file);
    this.close();
    input.value = '';
  }

  openCamera(): void {
    this.cameraError = '';
    this.stage = 'camera';
    // Defer getUserMedia so Angular has rendered the <video> element first
    setTimeout(() => {
      navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false })
        .then(stream => {
          this.zone.run(() => {
            this.stream = stream;
            const video = this.videoEl.nativeElement;
            video.srcObject = stream;
            video.play();
          });
        })
        .catch(err => {
          this.zone.run(() => {
            this.cameraError = `Camera unavailable: ${err.message || err}`;
            this.stage = 'picker';
          });
        });
    }, 100);
  }

  capture(): void {
    const video = this.videoEl.nativeElement;
    const canvas = this.canvasEl.nativeElement;
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    const ctx = canvas.getContext('2d')!;
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    this.previewDataUrl = canvas.toDataURL('image/jpeg', 0.92);
    canvas.toBlob(blob => {
      this.zone.run(() => {
        this.capturedBlob = blob;
        this.stage = 'preview';
        this.stopStream();
      });
    }, 'image/jpeg', 0.92);
  }

  retake(): void {
    this.previewDataUrl = null;
    this.capturedBlob = null;
    this.stage = 'picker';
  }

  confirm(): void {
    if (!this.capturedBlob) return;
    const file = new File([this.capturedBlob], `capture-${Date.now()}.jpg`, { type: 'image/jpeg' });
    this.fileSelected.emit(file);
    this.close();
  }

  close(): void {
    this.stopStream();
    this.stage = 'picker';
    this.previewDataUrl = null;
    this.capturedBlob = null;
    this.cameraError = '';
    this.closed.emit();
  }

  private stopStream(): void {
    if (this.stream) {
      this.stream.getTracks().forEach(t => t.stop());
      this.stream = null;
    }
  }

  ngOnDestroy(): void {
    this.stopStream();
  }
}
