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
  @ViewChild('cropCanvas') cropCanvasEl!: ElementRef<HTMLCanvasElement>;

  stage: 'picker' | 'camera' | 'preview' | 'crop' = 'picker';
  previewDataUrl: string | null = null;
  capturedBlob: Blob | null = null;
  cameraError = '';

  // Crop state
  cropSel: { x: number; y: number; w: number; h: number } | null = null;
  private cropImg: HTMLImageElement | null = null;
  private cropScaleX = 1;
  private cropScaleY = 1;
  private cropDragging = false;
  private cropDragStart: { x: number; y: number } | null = null;

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
    const vw = video.videoWidth || 640;
    const vh = video.videoHeight || 480;
    // 90° clockwise: output width = input height, output height = input width
    canvas.width = vh;
    canvas.height = vw;
    const ctx = canvas.getContext('2d')!;
    ctx.translate(canvas.width, 0);
    ctx.rotate(Math.PI / 2);
    ctx.drawImage(video, 0, 0, vw, vh);
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

  // ── Crop ──────────────────────────────────────────────────────────

  enterCrop(): void {
    this.cropSel = null;
    this.cropImg = null;
    this.stage = 'crop';
    setTimeout(() => this.initCropCanvas(), 60);
  }

  private initCropCanvas(): void {
    const canvas = this.cropCanvasEl?.nativeElement;
    if (!canvas || !this.previewDataUrl) return;
    const img = new Image();
    img.onload = () => {
      this.cropImg = img;
      const displayW = canvas.parentElement?.getBoundingClientRect().width ?? 440;
      const displayH = Math.round(displayW * (img.naturalHeight / img.naturalWidth));
      canvas.width = displayW;
      canvas.height = displayH;
      this.cropScaleX = img.naturalWidth / displayW;
      this.cropScaleY = img.naturalHeight / displayH;
      const ctx = canvas.getContext('2d')!;
      ctx.drawImage(img, 0, 0, displayW, displayH);
    };
    img.src = this.previewDataUrl;
  }

  private redrawCrop(): void {
    const canvas = this.cropCanvasEl?.nativeElement;
    if (!canvas || !this.cropImg) return;
    const ctx = canvas.getContext('2d')!;
    ctx.drawImage(this.cropImg, 0, 0, canvas.width, canvas.height);
    const sel = this.cropSel;
    if (sel && sel.w > 2 && sel.h > 2) {
      // Darken everything outside the selection
      ctx.fillStyle = 'rgba(0,0,0,0.52)';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      // Re-draw the selected region at full brightness
      ctx.drawImage(
        this.cropImg,
        sel.x * this.cropScaleX, sel.y * this.cropScaleY,
        sel.w * this.cropScaleX, sel.h * this.cropScaleY,
        sel.x, sel.y, sel.w, sel.h
      );
      // Dashed selection border
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 1.5;
      ctx.setLineDash([5, 3]);
      ctx.strokeRect(sel.x + 0.5, sel.y + 0.5, sel.w - 1, sel.h - 1);
      ctx.setLineDash([]);
    }
  }

  onCropMouseDown(e: MouseEvent): void {
    const rect = (e.currentTarget as HTMLCanvasElement).getBoundingClientRect();
    this.cropDragStart = { x: e.clientX - rect.left, y: e.clientY - rect.top };
    this.cropSel = { x: this.cropDragStart.x, y: this.cropDragStart.y, w: 0, h: 0 };
    this.cropDragging = true;
  }

  onCropMouseMove(e: MouseEvent): void {
    if (!this.cropDragging || !this.cropDragStart) return;
    const rect = (e.currentTarget as HTMLCanvasElement).getBoundingClientRect();
    const x = Math.max(0, Math.min(e.clientX - rect.left, rect.width));
    const y = Math.max(0, Math.min(e.clientY - rect.top, rect.height));
    this.cropSel = {
      x: Math.min(x, this.cropDragStart.x),
      y: Math.min(y, this.cropDragStart.y),
      w: Math.abs(x - this.cropDragStart.x),
      h: Math.abs(y - this.cropDragStart.y)
    };
    this.redrawCrop();
  }

  onCropMouseUp(): void {
    this.cropDragging = false;
  }

  get cropReady(): boolean {
    return !!this.cropSel && this.cropSel.w >= 10 && this.cropSel.h >= 10;
  }

  applyCrop(): void {
    if (!this.cropReady || !this.cropImg || !this.cropSel) return;
    const { x, y, w, h } = this.cropSel;
    const sx = Math.round(x * this.cropScaleX);
    const sy = Math.round(y * this.cropScaleY);
    const sw = Math.round(w * this.cropScaleX);
    const sh = Math.round(h * this.cropScaleY);
    const out = document.createElement('canvas');
    out.width = sw;
    out.height = sh;
    out.getContext('2d')!.drawImage(this.cropImg, sx, sy, sw, sh, 0, 0, sw, sh);
    const dataUrl = out.toDataURL('image/jpeg', 0.92);
    this.previewDataUrl = dataUrl;
    this.capturedBlob = this.dataUrlToBlob(dataUrl);
    this.cropSel = null;
    this.cropImg = null;
    this.stage = 'preview';
  }

  private dataUrlToBlob(dataUrl: string): Blob {
    const [header, base64] = dataUrl.split(',');
    const mime = header.match(/:(.*?);/)![1];
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return new Blob([bytes], { type: mime });
  }

  cancelCrop(): void {
    this.cropSel = null;
    this.cropImg = null;
    this.stage = 'preview';
  }

  // ── Stream ────────────────────────────────────────────────────────

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
