import { DocType, ListingType } from './comic.enums';

export interface PagedResponse<T> {
  items: T[];
  totalCount: number;
  pageNumber: number;
  pageSize: number;
  totalPages: number;
}

export interface ComicNumber {
  volume: number | null;
  number: number | null;
  sentinel: string | null;
}

export interface CGCCondition {
  label: string | null;
  grade: number | null;
  pageQuality: string | null;
  pedigree: string | null;
  signature: boolean | null;
  degreeOfRestoration: string | null;
  graderNotes: string | null;
}

export interface CBCSCondition {
  label: string | null;
  grade: number | null;
  pageQuality: string | null;
  pedigree: string | null;
  signature: boolean | null;
  degreeOfRestoration: string | null;
}

export interface ComicCondition {
  isGraded: boolean;
  certificationCompany: string | null;
  certificationId: string | null;
  cgcCondition: CGCCondition | null;
  cbcsCondition: CBCSCondition | null;
  notCertifiedLabel: string | null;
  notCertifiedGrade: number | null;
  notCertifiedPageQuality: string | null;
  notCertifiedPedigree: string | null;
  notCertifiedDegreeOfRestoration: string | null;
  notCertifiedSignature: boolean | null;
}

export interface GoCollectInfo {
  gcIndex: number | null;
  gcSlug: string | null;
  gcUrl: string | null;
  gcSeries: string | null;
  importDate: string | null;
}

export interface TradeInfo {
  desiredGrade: number | null;
  offeredGrade: number | null;
  offerAccepted: boolean | null;
  tradeReceived: boolean | null;
  tradeNotes: string | null;
  offeredBy: string | null;
  offeredAt: string | null;
  tradeFrontImageId: string | null;
  tradeSmallFrontImageId: string | null;
  tradeBackImageId: string | null;
  tradeSmallBackImageId: string | null;
}

export interface GrandComicDBInfo {
  gcdbIssueId: number | null;
  gcdbSeriesId: number | null;
  issueUrl: string | null;
  seriesUrl: string | null;
}

export interface Comic {
  id: number;
  title: string;
  series: string;
  number: ComicNumber | null;
  publisher: string | null;
  publishedDate: string | null;
  era: string | null;
  variant: string | null;
  printRun: number | null;
  barCode: string | null;
  keyIssue: string | null;
  writer: string[] | null;
  artist: string[] | null;
  comicCondition: ComicCondition | null;
  defects: string | null;
  pricePaid: number | null;
  dateAcquired: string | null;
  purchasedFrom: string | null;
  purchaseReferenceURL: string | null;
  ebayListingUrl?: string | null;
  salePrice: number | null;
  dateSold: string | null;
  soldTo: string | null;
  isForSale: boolean | null;
  listingType?: ListingType | null;
  expectedValue?: number | null;
  trade?: TradeInfo | null;
  personalEstimate: number | null;
  targetPrice: number | null;
  collectionGroup: number | null;
  docType: DocType | string | null;
  storageLocation: string | null;
  goCollectInfo: GoCollectInfo | null;
  grandComicDBInfo: GrandComicDBInfo | null;
  smallCachedImageId: string | null;
  largeCachedImageId: string | null;
  smallBackImageId: string | null;
  largeBackImageId: string | null;
  personalNotes: string | null;
  publicNotes: string | null;
  viewCount?: number | null;
  sold?: boolean | null;
  items?: Comic[];
}