
import { Component, OnInit, OnDestroy, Output, EventEmitter } from '@angular/core';
import { forkJoin } from 'rxjs';
import { AgGridModule } from 'ag-grid-angular';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Comic } from '../comic';
import { ComicService } from '../comic.service';
import { ConfigService, ComicEnums, GradeOption } from '../config.service';
import { ColDef, CellClickedEvent, RowClickedEvent, GetRowIdParams } from 'ag-grid-community';
import { DeleteButtonCellRendererComponent } from './delete-button-cell-renderer.component';
import { NumberCellEditorComponent } from './number-cell-editor.component';

@Component({
    selector: 'app-standalone-list',
    templateUrl: './standalone-list.component.html',
    styleUrls: ['./standalone-list.component.css'],
    imports: [AgGridModule, FormsModule, CommonModule]
})
export class StandaloneListComponent implements OnInit, OnDestroy {

  @Output() comicSelected = new EventEmitter<number>();

  comics: Comic[] = [];

  dateStringFormatter = (params: any): string => {
    const val = params.value;
    if (!val) return '';
    // String values: just show first 10 chars (YYYY-MM-DD), handles ISO strings too
    if (typeof val === 'string') return val.substring(0, 10);
    // Date object fallback: format via UTC to avoid timezone shift
    if (val instanceof Date && !isNaN(val.getTime())) {
      const pad = (n: number) => n.toString().padStart(2, '0');
      return `${val.getUTCFullYear()}-${pad(val.getUTCMonth() + 1)}-${pad(val.getUTCDate())}`;
    }
    return '';
  }

  getRowId = (params: GetRowIdParams) => String(params.data.id);

  columnDefs: ColDef[] = [
    {
      headerName: "",
      field: "id",
      cellRenderer: DeleteButtonCellRendererComponent,
      width: 48,
      minWidth: 48,
      maxWidth: 48,
      pinned: 'left',
      suppressMovable: true,
      sortable: false,
      filter: false,
      editable: false,
      resizable: false
    },
    {
      headerName: "Internal ID",
      field: "id",
      cellStyle: {'user-select': 'text'},
      filter: true,
      maxWidth: 140,
      sortable: false
    },
    {
      headerName: "Title",
      field: "title",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 360,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "Number",
      field: "number.number",
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 104,
      cellEditor: NumberCellEditorComponent,
      cellEditorPopup: true,
      valueGetter: (params: any) => {
        const n = params.data?.number;
        if (!n) return null;
        if (n.number != null) return String(n.number);
        if (n.sentinel) return n.sentinel;
        return null;
      },
      valueSetter: (params: any) => {
        // Value already passed API validation in the editor — just determine structure.
        const v = String(params.newValue ?? '').trim();
        if (!v) return false;
        if (!params.data.number) {
          params.data.number = { volume: null, number: null, sentinel: null };
        }
        if (/^\d+$/.test(v)) {
          params.data.number.number = parseInt(v, 10);
          params.data.number.sentinel = null;
        } else {
          params.data.number.sentinel = v;
          params.data.number.number = null;
        }
        return true;
      }
    },
    {
      headerName: "Series",
      field: "series",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 240,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "Publisher",
      field: "publisher",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 160,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "Volume",
      field: "number.volume",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 104,
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: {
        min: 0,
        max: 10000
      }
    },
    {
      headerName: "GCIN",
      field: "goCollectInfo.gcIndex",
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 140,
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: {
        min: 0,
        max: 10000000
      }
    },
    {
      headerName: "GC Series",
      field: "goCollectInfo.gcSeries",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 240,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "GC URL",
      field: "goCollectInfo.gcUrl",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 240,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 512
      }
    },
    {
      headerName: "BarCode",
      field: "barCode",
      editable: true,
      maxWidth: 130,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 20
      }
    },
    {
      headerName: "GCDB",
      field: "grandComicDBInfo.gcdbIssueId",
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 140,
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: {
        min: 0,
        max: 10000000
      }
    },
    {
      headerName: "GCDB Series",
      field: "grandComicDBInfo.gcdbSeriesId",
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 140,
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: {
        min: 0,
        max: 10000000
      }
    },
    {
      headerName: "Collection Group",
      field: "collectionGroup",
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 140,
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: {
        min: 0,
        max: 10000000
      }
    },
    {
      headerName: "Storage Location",
      field: "storageLocation",
      filter: true,
      sortable: true,
      editable: true,
      resizable: true,
    },
    {
      headerName: "Type",
      field: "docType",
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: { maxLength: 10 }
    },
    {
      headerName: "Estimate",
      field: "personalEstimate",
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 140,
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: {
        precision: 2,
        step: 0.25,
        showStepperButtons: true
      },
      valueFormatter: (params) => {
        return params.value ? `$${params.value.toFixed(2)}` : '';
      }
    },
    {
      headerName: "Target Price",
      field: "targetPrice",
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 140,
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: {
        precision: 2,
        step: 0.25,
        showStepperButtons: true
      },
      valueFormatter: (params) => {
        return params.value ? `$${params.value.toFixed(2)}` : '';
      }
    },
    {
      headerName: "Sale Price",
      field: "salePrice",
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 140,
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: {
        precision: 2,
        step: 0.25,
        showStepperButtons: true
      },
      valueFormatter: (params) => {
        return params.value ? `$${params.value.toFixed(2)}` : '';
      }
    },
    {
      headerName: "Price Paid",
      field: "pricePaid",
      filter: true,
      sortable: true,
      maxWidth: 140,
      editable: true,
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: {
        precision: 2,
        step: 0.25,
        showStepperButtons: true
      },
      valueFormatter: (params) => {
        return params.value ? `$${params.value.toFixed(2)}` : '';
      }
    },
    {
      headerName: "Certified Grade",
      field: "comicCondition.cgcCondition.grade",
      filter: true,
      sortable: true,
      maxWidth: 140,
      editable: true,
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: {
        precision: 1,
        step: 0.5,
        min: 0,
        max: 10,
        showStepperButtons: true
      },
      valueFormatter: (params) => {
        return params.value ? `${params.value.toFixed(1)}` : '';
      }
    },
    {
      headerName: "Date Acquired",
      field: "dateAcquired",
      filter: true,
      sortable: true,
      maxWidth: 140,
      editable: true,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: { inputType: 'date' },
      valueFormatter: this.dateStringFormatter
    },
    {
      headerName: "Published Date",
      field: "publishedDate",
      filter: true,
      sortable: true,
      maxWidth: 140,
      editable: true,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: { inputType: 'date' },
      valueFormatter: this.dateStringFormatter
    },
    {
      headerName: "Date Sold",
      field: "dateSold",
      filter: true,
      sortable: true,
      maxWidth: 140,
      editable: true,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: { inputType: 'date' },
      valueFormatter: this.dateStringFormatter
    },
    {
      headerName: "Cert Id",
      field: "comicCondition.certificationId",
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 140,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 20
      }
    },
    {
      headerName: "Purchased From",
      field: "purchasedFrom",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "Purchased URL",
      field: "purchaseReferenceURL",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "Personal Notes",
      field: "personalNotes",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 2560
      }
    },
    {
      headerName: "Public Notes",
      field: "publicNotes",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 2560
      }
    },
    {
      headerName: "Cert Company",
      field: "comicCondition.certificationCompany",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "Not Certified Label",
      field: "comicCondition.notCertifiedLabel",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "Not Certified Grade",
      field: "comicCondition.notCertifiedGrade",
      editable: true,
      minWidth: 120,
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: {
        precision: 1,
        step: 0.5,
        min: 0,
        max: 10
      },
      valueFormatter: (params) => {
        return params.value ? `${params.value.toFixed(1)}` : '';
      }
    },
    {
      headerName: "Not Certified Page Quality",
      field: "comicCondition.notCertifiedPageQuality",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "Not Certified Pedigree",
      field: "comicCondition.notCertifiedPedigree",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "Not Certified Degree Of Restoration",
      field: "comicCondition.notCertifiedDegreeOfRestoration",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "Not Certified Signature",
      field: "comicCondition.notCertifiedSignature",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "For Sale",
      field: "isForSale",
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 100,
      cellEditor: 'agCheckboxCellEditor',
      cellRenderer: 'agCheckboxCellRenderer'
    },
    {
      headerName: "Enable Bid",
      field: "enableBid",
      filter: true,
      sortable: true,
      editable: true,
      maxWidth: 110,
      cellEditor: 'agCheckboxCellEditor',
      cellRenderer: 'agCheckboxCellRenderer'
    },
    {
      headerName: "Small Image Id",
      field: "smallCachedImageId",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    },
    {
      headerName: "Large Image Id",
      field: "largeCachedImageId",
      resizable: true,
      filter: true,
      sortable: true,
      editable: true,
      minWidth: 120,
      cellEditor: 'agTextCellEditor',
      cellEditorParams: {
        maxLength: 256
      }
    }
  ];

  rowData: any[] = [];
  dataLoaded = false;
  refreshing = false;
  gridSearch = '';

  // ── Enum reference data ───────────────────────────────────────────────────
  enums: ComicEnums = { coverVariants: [], gradingCompanies: [], grades: [], pageQualities: [] };

  // ── Add Set modal ─────────────────────────────────────────────────────────
  showAddSetModal = false;
  addSetCollectionGroup: number | null = null;
  addSetName = '';
  addSetSaving = false;
  addSetError = '';
  addSetExistingGroups: number[] = [];

  // ── Delete Set modal ──────────────────────────────────────────────────────
  showDeleteSetModal = false;
  deleteSetAvailableSets: Comic[] = [];
  deleteSetSelected: Comic | null = null;
  deleteSetSaving = false;
  deleteSetError = '';
  deleteSetConfirmFull = false;

  // ── Add To Set modal ──────────────────────────────────────────────────────
  showAddToSetModal = false;
  addToSetStep = 1;
  addToSetAvailableSets: Comic[] = [];
  addToSetUnassigned: Comic[] = [];
  addToSetSelectedSet: Comic | null = null;
  addToSetSearchTerm = '';
  addToSetSearchResults: Comic[] = [];
  addToSetSaving = false;
  addToSetError = '';

  // ── Quick Add wizard ──────────────────────────────────────────────────────
  qaStep = 1;
  qaSaving = false;
  qaSuccess: string | false = false;
  qaError = '';
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

  qaNext() { this.qaStep = 2; }
  qaToGraded() { this.qaStep = 3; }
  qaBack() { this.qaStep--; }

  qaReset() {
    this.qaStep = 1;
    this.qaSaving = false;
    this.qaSuccess = false;
    this.qaError = '';
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
    this.qaSaving = true;
    this.qaError = '';
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
        this.dataLoaded = false;
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

  onFirstDataRendered(params: any): void {
    params.api.autoSizeAllColumns();
  }

  onCellClicked( event: CellClickedEvent) {
    console.log('Cell Clicked:', event.value);
  }

  onRowClicked( event: RowClickedEvent) {
    console.log('Row Clicked:', event.data);
    this.rowData = [event.data];
    this.comicSelected.emit(event.data.id);
  }

  addNewRow() {
    const newRow: Comic = {
      id: -1,
      title: '- Enter Title Name -',
      series: '- Enter Series Name -',
      number: { volume: null, number: null, sentinel: null },
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
        certificationCompany: null,
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
      pricePaid: 0,
      dateAcquired: null,
      purchasedFrom: null,
      purchaseReferenceURL: null,
      salePrice: null,
      dateSold: null,
      soldTo: null,
      isForSale: false,
      personalEstimate: 0,
      targetPrice: 0,
      collectionGroup: -1,
      docType: 'COMIC',
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
    this.comicService.addComic(newRow).subscribe(returned => {
      this.rowData = [...this.rowData, returned];
    });
  }

  onCellValueChanged(event: any) {
    console.log('Cell Update:', event.data);
    this.comicService.updateComic(event.data).subscribe();
    // update of table is implicit i think
  }

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
    this.addSetName = '';
    this.addSetError = '';
    this.comicService.getSets().subscribe(sets => {
      this.addSetExistingGroups = sets
        .filter(c => c.collectionGroup != null)
        .map(c => c.collectionGroup!);
    });
    this.showAddSetModal = true;
  }

  cancelAddSet(): void {
    this.showAddSetModal = false;
    this.addSetCollectionGroup = null;
    this.addSetName = '';
    this.addSetError = '';
    this.addSetExistingGroups = [];
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
        this.dataLoaded = true;
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

  openAddToSet(): void {
    forkJoin([this.comicService.getSets(), this.comicService.getSingleComics()]).subscribe(([sets, singles]) => {
      this.addToSetAvailableSets = sets;
      this.addToSetUnassigned = singles;
      this.addToSetStep = 1;
      this.addToSetSelectedSet = null;
      this.addToSetSearchTerm = '';
      this.addToSetSearchResults = [];
      this.addToSetError = '';
      this.showAddToSetModal = true;
    });
  }

  selectSetForAddTo(set: Comic): void {
    this.addToSetSelectedSet = set;
    this.addToSetSearchTerm = '';
    this.addToSetSearchResults = [];
    this.addToSetError = '';
    this.addToSetStep = 2;
  }

  filterAddToSetResults(): void {
    const term = this.addToSetSearchTerm.trim().toLowerCase();
    if (!term) {
      this.addToSetSearchResults = [];
      return;
    }
    this.addToSetSearchResults = this.addToSetUnassigned.filter(c =>
      c.title?.toLowerCase().includes(term) ||
      c.series?.toLowerCase().includes(term)
    );
  }

  confirmAddToSet(comic: Comic): void {
    if (!this.addToSetSelectedSet) return;
    this.addToSetSaving = true;
    this.addToSetError = '';
    const updated: Comic = { ...comic, collectionGroup: this.addToSetSelectedSet.collectionGroup };
    this.comicService.updateComic(updated).subscribe({
      next: () => {
        this.comics = this.comics.map(c => c.id === updated.id ? updated : c);
        this.addToSetSaving = false;
        this.showAddToSetModal = false;
      },
      error: () => {
        this.addToSetError = 'Failed to add to set. Please try again.';
        this.addToSetSaving = false;
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

  refreshGrid(): void {
    this.refreshing = true;
    this.comicService.getRemoteComics().subscribe({
      next: comics => {
        this.comics = comics;
        this.dataLoaded = true;
        this.refreshing = false;
      },
      error: () => { this.refreshing = false; }
    });
  }

}