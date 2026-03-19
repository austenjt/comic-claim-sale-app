export interface ShippingEstimate {
  bookCount: number;
  packagingTier: 'FLAT_RATE' | 'GEMINI_MAILER' | 'COMIC_BOX' | 'FREE' | 'NONE';
  packageWeightLbs: number;
  shippingZone: number;
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
  paymentStatus?: 'UNPAID' | 'PARTIAL' | 'PAID' | null;
  customerNotes?: string | null;
  adminNotes?: string | null;
  shippingEstimate?: ShippingEstimate | null;
}
