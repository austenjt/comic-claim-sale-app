export interface CartItem {
  comicId: string;
  comicTitle: string;
  comicNumber: string | null;
  price: number;
  claimedAt: string;
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
}
