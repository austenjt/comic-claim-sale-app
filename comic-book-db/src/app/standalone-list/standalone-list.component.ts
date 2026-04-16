
import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Comic } from '../comic';
import { ComicService } from '../comic.service';
import { ConfigService, ComicEnums } from '../config.service';

@Component({
    selector: 'app-standalone-list',
    templateUrl: './standalone-list.component.html',
    styleUrls: ['./standalone-list.component.css'],
    imports: [FormsModule, CommonModule]
})
export class StandaloneListComponent implements OnInit, OnDestroy {

  comics: Comic[] = [];

  // ── Enum reference data ───────────────────────────────────────────────────
  enums: ComicEnums = { coverVariants: [], gradingCompanies: [], grades: [], pageQualities: [] };

  // ── Add Set modal ─────────────────────────────────────────────────────────
  showAddSetModal = false;
  addSetCollectionGroup: number | null = null;
  addSetGroupAutoSet = false;
  addSetName = '';
  addSetSaving = false;
  addSetError = '';
  addSetExistingGroups: number[] = [];

  // ── View Sets modal ───────────────────────────────────────────────────────
  showViewSetsModal = false;
  viewSetsList: Comic[] = [];
  viewSetsLoading = false;
  viewSetsError = '';

  // ── Edit Set Name modal ───────────────────────────────────────────────────
  showEditSetNameModal = false;
  editSetNameStep = 1;
  editSetNameAvailableSets: Comic[] = [];
  editSetNameSelected: Comic | null = null;
  editSetNameNewName = '';
  editSetNameSaving = false;
  editSetNameError = '';

  // ── Delete Set modal ──────────────────────────────────────────────────────
  showDeleteSetModal = false;
  deleteSetAvailableSets: Comic[] = [];
  deleteSetSelected: Comic | null = null;
  deleteSetSaving = false;
  deleteSetError = '';
  deleteSetConfirmFull = false;


  // ── Quick Add wizard ──────────────────────────────────────────────────────
  qaStep = 1;
  qaSaving = false;
  qaSuccess: string | false = false;
  qaError = '';
  qaShowFieldErrors = false;
  qaSeries: string[] = [];
  private qaLastSeries = '';

  readonly ERA_OPTIONS = ['Golden Age', 'Silver Age', 'Bronze Age', 'Copper Age', 'Modern Age'];

  qa = {
    // Step 1 — identity
    title: '',
    series: '',
    number: '',
    publisher: '',
    era: '',
    publishedDate: '',
    variant: '',
    barCode: '',
    printRun: null as number | null,
    keyIssue: '',
    writer: '',
    artist: '',
    // Step 2 — pricing / acquisition / notes
    isForSale: true,
    salePrice: null as number | null,
    targetPrice: null as number | null,
    personalEstimate: null as number | null,
    pricePaid: null as number | null,
    dateAcquired: '',
    purchasedFrom: '',
    purchaseReferenceURL: '',
    storageLocation: '',
    defects: '',
    publicNotes: '',
    personalNotes: '',
    // Step 3 — grading
    gradingCompany: '',
    grade: null as number | null,
    certificationId: '',
    pageQuality: '',
    pedigree: null as boolean | null,
    signed: false
  };

  qaDefaultSeries() {
    if (!this.qa.series && !this.qaLastSeries) this.qa.series = this.qa.title;
  }

  get qaCanNext(): boolean {
    return !!this.qa.title.trim();
  }

  get qaCanSave(): boolean {
    if (!this.qa.title.trim()) return false;
    if (!this.qa.number.trim()) return false;
    if (this.qa.isForSale && (this.qa.salePrice === null || this.qa.salePrice === undefined)) return false;
    if (this.qaStep === 3) {
      if (!this.qa.gradingCompany) return false;
      if (this.qa.grade === null || this.qa.grade === undefined) return false;
    }
    return true;
  }

  qaNext() {
    this.qaShowFieldErrors = true;
    this.qaError = '';
    if (!this.qa.title.trim()) { this.qaError = 'Title is required.'; return; }
    this.qaShowFieldErrors = false;
    this.qaStep = 2;
  }

  qaToGraded() {
    this.qaShowFieldErrors = true;
    this.qaError = '';
    if (!this.qa.title.trim()) { this.qaError = 'Title is required.'; return; }
    if (!this.qa.number.trim()) { this.qaError = 'Number is required.'; return; }
    if (this.qa.isForSale && (this.qa.salePrice === null || this.qa.salePrice === undefined)) {
      this.qaError = 'Sale Price is required when For Sale is checked.'; return;
    }
    this.qaShowFieldErrors = false;
    this.qaStep = 3;
  }

  qaBack() { this.qaStep--; }

  qaReset() {
    this.qaStep = 1;
    this.qaSaving = false;
    this.qaSuccess = false;
    this.qaError = '';
    this.qaShowFieldErrors = false;
    this.qa = {
      title: '', series: this.qaLastSeries, number: '', publisher: '', era: '', publishedDate: '',
      variant: '', barCode: '', printRun: null, keyIssue: '', writer: '', artist: '',
      isForSale: true, salePrice: null, targetPrice: null, personalEstimate: null,
      pricePaid: null, dateAcquired: '', purchasedFrom: '', purchaseReferenceURL: '',
      storageLocation: '', defects: '', publicNotes: '', personalNotes: '',
      gradingCompany: '', grade: null, certificationId: '', pageQuality: '', pedigree: null, signed: false
    };
  }

  qaSave() {
    this.qaShowFieldErrors = true;
    this.qaError = '';
    if (!this.qaCanSave) return;
    this.qaShowFieldErrors = false;
    this.qaSaving = true;
    const numRaw = this.qa.number.trim();
    const parsedNum = parseFloat(numRaw);
    const comicNumber = numRaw === ''
      ? null
      : !isNaN(parsedNum)
        ? { volume: null, number: parsedNum, sentinel: null }
        : { volume: null, number: null, sentinel: numRaw };

    const company = this.qa.gradingCompany;
    let comicCondition: any;
    if (this.qaStep === 3 && company && company !== 'NOT CERTIFIED') {
      // Graded slab
      const pedigreeStr = this.qa.pedigree ? 'Yes' : null;
      comicCondition = {
        isGraded: true,
        certificationCompany: company,
        certificationId: this.qa.certificationId.trim() || null,
        cgcCondition: company === 'CGC' ? {
          label: null,
          grade: this.qa.grade,
          pageQuality: this.qa.pageQuality || null,
          pedigree: pedigreeStr,
          signature: this.qa.signed,
          degreeOfRestoration: null,
          graderNotes: null
        } : null,
        cbcsCondition: company === 'CBCS' ? {
          label: null,
          grade: this.qa.grade,
          pageQuality: this.qa.pageQuality || null,
          pedigree: pedigreeStr,
          signature: this.qa.signed,
          degreeOfRestoration: null
        } : null,
        notCertifiedLabel: null,
        notCertifiedGrade: (company === 'PGX') ? this.qa.grade : null,
        notCertifiedPageQuality: (company === 'PGX') ? (this.qa.pageQuality || null) : null,
        notCertifiedPedigree: (company === 'PGX') ? pedigreeStr : null,
        notCertifiedDegreeOfRestoration: null,
        notCertifiedSignature: (company === 'PGX') ? this.qa.signed : null
      };
    } else {
      // Not graded / saved from step 2
      comicCondition = {
        isGraded: false,
        certificationCompany: 'NOT CERTIFIED',
        certificationId: null,
        cgcCondition: null,
        cbcsCondition: null,
        notCertifiedLabel: null,
        notCertifiedGrade: null,
        notCertifiedPageQuality: null,
        notCertifiedPedigree: null,
        notCertifiedDegreeOfRestoration: null,
        notCertifiedSignature: null
      };
    }

    const splitList = (s: string) => s.split(',').map(x => x.trim()).filter(x => x.length > 0);

    const newComic: Comic = {
      id: -1,
      title: this.qa.title.trim() || '- Enter Title Name -',
      series: this.qa.series.trim() || '- Enter Series Name -',
      number: comicNumber,
      publisher: this.qa.publisher.trim() || null,
      publishedDate: this.qa.publishedDate.trim() || null,
      era: this.qa.era || null,
      variant: this.qa.variant.trim() || null,
      printRun: this.qa.printRun,
      barCode: this.qa.barCode.trim() || null,
      keyIssue: this.qa.keyIssue.trim() || null,
      writer: splitList(this.qa.writer),
      artist: splitList(this.qa.artist),
      comicCondition,
      defects: this.qa.defects.trim() || null,
      pricePaid: this.qa.pricePaid,
      dateAcquired: this.qa.dateAcquired.trim() || null,
      purchasedFrom: this.qa.purchasedFrom.trim() || null,
      purchaseReferenceURL: this.qa.purchaseReferenceURL.trim() || null,
      salePrice: this.qa.salePrice,
      dateSold: null,
      soldTo: null,
      isForSale: this.qa.isForSale,
      personalEstimate: this.qa.personalEstimate,
      targetPrice: this.qa.targetPrice,
      collectionGroup: null,
      docType: 'COMIC',
      storageLocation: this.qa.storageLocation.trim() || null,
      goCollectInfo: null,
      grandComicDBInfo: null,
      smallCachedImageId: null,
      largeCachedImageId: null,
      smallBackImageId: null,
      largeBackImageId: null,
      personalNotes: this.qa.personalNotes.trim() || null,
      publicNotes: this.qa.publicNotes.trim() || null
    };

    this.comicService.addComic(newComic).subscribe({
      next: returned => {
        this.comics = [...this.comics, returned];
        this.qaSaving = false;
        this.qaLastSeries = this.qa.series;
        const numLabel = this.comicNumberLabel(returned);
        this.qaSuccess = `Saved "${returned.title}${numLabel}" — Ready for next entry.`;
        setTimeout(() => this.qaReset(), 3000);
      },
      error: () => {
        const title = this.qa.title.trim() || 'comic';
        const numLabel = this.qa.number.trim() ? ` #${this.qa.number.trim()}` : '';
        this.qaError = `Failed to save "${title}${numLabel}". Please try again.`;
        this.qaSaving = false;
      }
    });
  }
  // ─────────────────────────────────────────────────────────────────────────

  constructor(private comicService: ComicService, private configService: ConfigService) { }

  ngOnInit() {
    this.enums = this.configService.getEnums();
    this.comicService.getSeriesList().subscribe(list => { this.qaSeries = list; });
  }

  ngOnDestroy() { }

  get addSetGroupConflict(): boolean {
    return this.addSetCollectionGroup != null &&
           this.addSetExistingGroups.includes(this.addSetCollectionGroup);
  }

  get addSetGroupInvalid(): boolean {
    const v = this.addSetCollectionGroup;
    return v == null || v < 1 || v > 9999;
  }

  openAddSet(): void {
    this.addSetCollectionGroup = null;
    this.addSetGroupAutoSet = false;
    this.addSetName = '';
    this.addSetError = '';
    this.comicService.getSets().subscribe(sets => {
      this.addSetExistingGroups = sets
        .filter(c => c.collectionGroup != null)
        .map(c => c.collectionGroup!);
      const next = this.addSetExistingGroups.length > 0
        ? Math.max(...this.addSetExistingGroups) + 1
        : 1;
      this.addSetCollectionGroup = next;
      this.addSetGroupAutoSet = true;
    });
    this.showAddSetModal = true;
  }

  cancelAddSet(): void {
    this.showAddSetModal = false;
    this.addSetCollectionGroup = null;
    this.addSetGroupAutoSet = false;
    this.addSetName = '';
    this.addSetError = '';
    this.addSetExistingGroups = [];
  }

  openViewSets(): void {
    this.showViewSetsModal = true;
    this.viewSetsLoading = true;
    this.viewSetsError = '';
    this.comicService.getSets().subscribe({
      next: sets => {
        this.viewSetsList = sets
          .filter(c => c.docType === 'SET')
          .sort((a, b) => (a.collectionGroup ?? 0) - (b.collectionGroup ?? 0));
        this.viewSetsLoading = false;
      },
      error: () => {
        this.viewSetsError = 'Failed to load sets.';
        this.viewSetsLoading = false;
      }
    });
  }

  openEditSetName(): void {
    this.editSetNameStep = 1;
    this.editSetNameSelected = null;
    this.editSetNameNewName = '';
    this.editSetNameError = '';
    this.editSetNameSaving = false;
    this.comicService.getSets().subscribe({
      next: sets => {
        this.editSetNameAvailableSets = sets
          .filter(c => c.docType === 'SET')
          .sort((a, b) => (a.collectionGroup ?? 0) - (b.collectionGroup ?? 0));
        this.showEditSetNameModal = true;
      },
      error: () => {
        this.editSetNameError = 'Failed to load sets.';
        this.showEditSetNameModal = true;
      }
    });
  }

  selectSetForRename(set: Comic): void {
    this.editSetNameSelected = set;
    this.editSetNameNewName = set.title ?? '';
    this.editSetNameError = '';
    this.editSetNameStep = 2;
  }

  confirmEditSetName(): void {
    if (!this.editSetNameSelected || !this.editSetNameNewName.trim()) return;
    this.editSetNameSaving = true;
    this.editSetNameError = '';
    const updated: Comic = { ...this.editSetNameSelected, title: this.editSetNameNewName.trim() };
    this.comicService.updateComic(updated).subscribe({
      next: () => {
        const idx = this.comics.findIndex(c => c.id === updated.id);
        if (idx !== -1) {
          this.comics[idx] = { ...this.comics[idx], title: updated.title };
          this.comics = [...this.comics];
        }
        this.editSetNameSaving = false;
        this.showEditSetNameModal = false;
      },
      error: () => {
        this.editSetNameError = 'Failed to rename set. Please try again.';
        this.editSetNameSaving = false;
      }
    });
  }

  cancelEditSetName(): void {
    this.showEditSetNameModal = false;
    this.editSetNameSelected = null;
    this.editSetNameNewName = '';
    this.editSetNameError = '';
    this.editSetNameSaving = false;
  }

  confirmAddSet(): void {
    if (!this.addSetCollectionGroup || this.addSetGroupInvalid || this.addSetGroupConflict) return;
    if (!this.addSetName.trim()) return;
    this.addSetSaving = true;
    this.addSetError = '';
    const setComic: Comic = {
      id: -1,
      title: this.addSetName.trim(),
      series: '',
      number: { volume: null, number: null, sentinel: 'SET' },
      publisher: null,
      publishedDate: null,
      era: null,
      variant: null,
      printRun: null,
      barCode: null,
      keyIssue: null,
      writer: [],
      artist: [],
      comicCondition: {
        isGraded: false,
        certificationCompany: 'NOT CERTIFIED',
        certificationId: null,
        cgcCondition: null,
        cbcsCondition: null,
        notCertifiedLabel: null,
        notCertifiedGrade: null,
        notCertifiedPageQuality: null,
        notCertifiedPedigree: null,
        notCertifiedDegreeOfRestoration: null,
        notCertifiedSignature: null
      },
      defects: null,
      pricePaid: null,
      dateAcquired: null,
      purchasedFrom: null,
      purchaseReferenceURL: null,
      salePrice: 0,
      dateSold: null,
      soldTo: null,
      isForSale: true,
      personalEstimate: null,
      targetPrice: null,
      collectionGroup: this.addSetCollectionGroup,
      docType: 'SET',
      storageLocation: null,
      goCollectInfo: null,
      grandComicDBInfo: null,
      smallCachedImageId: null,
      largeCachedImageId: null,
      smallBackImageId: null,
      largeBackImageId: null,
      personalNotes: null,
      publicNotes: null
    };
    this.comicService.addComic(setComic).subscribe({
      next: returned => {
        this.comics = [...this.comics, returned];
        this.addSetSaving = false;
        this.showAddSetModal = false;
        this.addSetCollectionGroup = null;
        this.addSetName = '';
        this.addSetExistingGroups = [];
      },
      error: () => {
        this.addSetError = 'Failed to create set. Please try again.';
        this.addSetSaving = false;
      }
    });
  }

  openDeleteSet(): void {
    this.comicService.getSets().subscribe(sets => {
      this.deleteSetAvailableSets = sets;
      this.deleteSetSelected = null;
      this.deleteSetError = '';
      this.deleteSetConfirmFull = false;
      this.showDeleteSetModal = true;
    });
  }

  selectSetForDelete(set: Comic): void {
    this.deleteSetSelected = set;
    this.deleteSetError = '';
    this.deleteSetConfirmFull = false;
  }

  requestFullDelete(): void {
    this.deleteSetError = '';
    this.deleteSetConfirmFull = true;
  }

  confirmDeleteSet(): void {
    if (!this.deleteSetSelected?.collectionGroup) return;
    this.deleteSetSaving = true;
    this.deleteSetError = '';
    this.comicService.deleteSet(this.deleteSetSelected.collectionGroup).subscribe({
      next: () => {
        this.comics = this.comics.filter(c => c.collectionGroup !== this.deleteSetSelected!.collectionGroup);
        this.deleteSetSaving = false;
        this.showDeleteSetModal = false;
        this.deleteSetSelected = null;
      },
      error: (err: any) => {
        this.deleteSetError = err?.error || 'Failed to delete set. Please try again.';
        this.deleteSetSaving = false;
      }
    });
  }

  confirmDeleteSetFully(): void {
    if (!this.deleteSetSelected?.collectionGroup) return;
    this.deleteSetSaving = true;
    this.deleteSetError = '';
    this.comicService.deleteSetFully(this.deleteSetSelected.collectionGroup).subscribe({
      next: () => {
        this.comics = this.comics.filter(c => c.collectionGroup !== this.deleteSetSelected!.collectionGroup);
        this.deleteSetSaving = false;
        this.showDeleteSetModal = false;
        this.deleteSetSelected = null;
        this.deleteSetConfirmFull = false;
      },
      error: (err: any) => {
        this.deleteSetError = err?.error || 'Failed to fully delete set. Please try again.';
        this.deleteSetSaving = false;
        this.deleteSetConfirmFull = false;
      }
    });
  }

  cancelDeleteSet(): void {
    this.showDeleteSetModal = false;
    this.deleteSetSelected = null;
    this.deleteSetError = '';
    this.deleteSetConfirmFull = false;
  }

  comicNumberLabel(comic: Comic): string {
    const n = comic.number;
    if (!n) return '';
    if (n.number != null) return ` #${n.number}`;
    if (n.sentinel) return ` ${n.sentinel}`;
    return '';
  }

}
