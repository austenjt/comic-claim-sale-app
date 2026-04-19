export interface ArchivedOrderItem {
  comicId?: string | null;
  comicTitle: string;
  comicNumber: string | null;
  price: number;
  claimedAt: string;
  collectionGroup?: number | null;
  wonViaBid?: boolean;
}

export interface ArchivedOrder {
  id: string;
  userId: string;
  userName: string;
  userEmail: string;
  items: ArchivedOrderItem[];
  discountAmount: number;
  discountDescription?: string;
  shippingCost?: number;
  createdAt: string;
  fulfilledAt: string;
  paymentStatus?: 'UNPAID' | 'PARTIAL' | 'PAID' | null;
  shipped?: boolean | null;
  trackingNumber?: string | null;
  customerNotes?: string | null;
  adminNotes?: string | null;
}
