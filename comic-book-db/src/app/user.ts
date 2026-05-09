export interface ShippingAddress {
  street1: string;
  street2?: string;
  city: string;
  state: string;
  zip: string;
  phone?: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  shippingAddress?: ShippingAddress | null;
  phone?: string;
  notes?: string;
  preferences?: string;
  venmoHandle?: string;
  paypalHandle?: string;
  ebayUsername?: string;
  cashAppHandle?: string;
  status: 'PENDING' | 'APPROVED' | 'SUSPENDED';
  isAdmin: boolean;
  createdDate?: string;
  approvedDate?: string;
}
