export interface ArchivedOrderItem {
  comicTitle: string;
  comicNumber: string | null;
  price: number;
  claimedAt: string;
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
}
