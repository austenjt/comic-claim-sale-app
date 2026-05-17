export type DiscountType = 'RAW_PERCENTAGE' | 'BUY_X_GET_ONE_FREE' | 'PERCENT_OFF_OVER_X_BOOKS' | 'FREE_SHIPPING_OVER_X_BOOKS';

export interface Discount {
  id: string;
  name: string;
  type: DiscountType;
  isActive: boolean;
  percentageOff: number;
  xBooks: number;
  excludeSets: boolean;
  /** When true, graded comics (CGC/CBCS) are excluded from this rule's count and discount. */
  excludeGraded?: boolean;
  createdAt: string;
}
