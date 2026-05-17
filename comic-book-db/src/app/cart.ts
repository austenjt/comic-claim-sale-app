import { ShippingAddress } from './user';

export interface CartDiscount {
  amount: number;
  description: string;
  excludesSets: boolean;
  excludesGraded?: boolean;
}

export interface ShippingEstimate {
  bookCount: number;
  packagingTier: 'GEMINI_MAILER' | 'COMIC_BOX' | 'LARGE_BOX' | 'SHORT_BOX' | 'NONE';
  estimatedCost: number;
  notes: string;
  isFree: boolean;
}

export interface CartItem {
  comicId: string;
  comicTitle: string;
  comicNumber: string | null;
  price: number;
  claimedAt: string;
  collectionGroup: number | null;
  isSetContainer?: boolean;
  /** True when the source comic is graded (CGC/CBCS). Snapshotted at cart-add time. */
  isGraded?: boolean;
  /** True when this item is a trade-in (negative credit, user sends comic to admin). */
  isTrade?: boolean;
}

export interface Cart {
  id: string;
  userId: string;
  userName: string;
  userEmail: string;
  items: CartItem[];
  status: 'OPEN' | 'SUBMITTED' | 'FULFILLED';
  createdAt: string;
  finalizeAfter?: string;
  finalizedAt?: string;
  fulfilledAt?: string;
  discountAmount?: number;
  discountDescription?: string | null;
  discountExcludesSets?: boolean | null;
  discountExcludesGraded?: boolean | null;
  discountBreakdown?: CartDiscount[] | null;
  shippingCost?: number;
  freeShippingApplied?: boolean;
  paymentStatus?: 'UNPAID' | 'PARTIAL' | 'PAID' | null;
  shipped?: boolean | null;
  trackingNumber?: string | null;
  customerNotes?: string | null;
  adminNotes?: string | null;
  invoiceNumber?: string | null;
  shippingEstimate?: ShippingEstimate | null;
  shippingAddress?: ShippingAddress | null;
  tradeReceived?: boolean | null;
}
