export interface ArchivedOrderItem {
  comicId?: string | null;
  comicTitle: string;
  comicNumber: string | null;
  price: number;
  claimedAt: string;
  collectionGroup?: number | null;
}

export interface ArchivedOrder {
  id: string;
  userId: string;
  userName: string;
  userEmail: string;
  items: ArchivedOrderItem[];
  discountAmount: number;
  discountDescription?: string;
  createdAt: string;
  fulfilledAt: string;
  paymentStatus?: 'UNPAID' | 'PARTIAL' | 'PAID' | null;
  customerNotes?: string | null;
  adminNotes?: string | null;
}
