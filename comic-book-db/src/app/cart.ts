export interface CartDiscount {
  amount: number;
  description: string;
  excludesSets: boolean;
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
  /** True when this item was won through a bidding cycle — cannot be returned. */
  wonViaBid?: boolean;
}

export interface Cart {
  id: string;
  userId: string;
  userName: string;
  userEmail: string;
  items: CartItem[];
  status: 'OPEN' | 'FINALIZING' | 'FINALIZED' | 'FULFILLED';
  createdAt: string;
  finalizeAfter?: string;
  finalizedAt?: string;
  fulfilledAt?: string;
  discountAmount?: number;
  discountDescription?: string | null;
  discountExcludesSets?: boolean | null;
  discountBreakdown?: CartDiscount[] | null;
  shippingCost?: number;
  paymentStatus?: 'UNPAID' | 'PARTIAL' | 'PAID' | null;
  customerNotes?: string | null;
  adminNotes?: string | null;
  shippingEstimate?: ShippingEstimate | null;
}
