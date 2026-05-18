export const ListingType = {
  NOT_LISTED: 'NOT_LISTED',
  FOR_SALE: 'FOR_SALE',
  WANTED: 'WANTED',
} as const;
export type ListingType = typeof ListingType[keyof typeof ListingType];

export const DocType = {
  COMIC: 'COMIC',
  SET: 'SET',
} as const;
export type DocType = typeof DocType[keyof typeof DocType];

export const NumberSentinel = {
  NEGATIVE_ONE: '-1',
  NN: 'NN',
  SET: 'SET',
} as const;
export type NumberSentinel = typeof NumberSentinel[keyof typeof NumberSentinel];

export const Era = {
  GOLDEN: 'Golden Age',
  SILVER: 'Silver Age',
  BRONZE: 'Bronze Age',
  COPPER: 'Copper Age',
  MODERN: 'Modern Age',
} as const;
export type Era = typeof Era[keyof typeof Era];

export const ERA_OPTIONS: ReadonlyArray<Era> = [
  Era.GOLDEN, Era.SILVER, Era.BRONZE, Era.COPPER, Era.MODERN,
];

export const GradingCompany = {
  CGC: 'CGC',
  CBCS: 'CBCS',
  PGX: 'PGX',
  NOT_CERTIFIED: 'NOT CERTIFIED',
} as const;
export type GradingCompany = typeof GradingCompany[keyof typeof GradingCompany];

export const CartStatus = {
  OPEN: 'OPEN',
  FINALIZING: 'FINALIZING',
  FINALIZED: 'FINALIZED',
  FULFILLED: 'FULFILLED',
  DELETED: 'DELETED',
} as const;
export type CartStatus = typeof CartStatus[keyof typeof CartStatus];

export const PaymentStatus = {
  PAID: 'PAID',
  UNPAID: 'UNPAID',
} as const;
export type PaymentStatus = typeof PaymentStatus[keyof typeof PaymentStatus];
