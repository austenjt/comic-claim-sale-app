export type DiscountType = 'RAW_PERCENTAGE' | 'BUY_X_GET_ONE_FREE' | 'PERCENTAGE_PER_X_BOOKS';

export interface Discount {
  id: string;
  name: string;
  type: DiscountType;
  isActive: boolean;
  percentageOff: number;
  xBooks: number;
  excludeSets: boolean;
  createdAt: string;
}
