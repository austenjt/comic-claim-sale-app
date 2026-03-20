export interface User {
  id: string;
  name: string;
  email: string;
  address?: string;
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

export interface SessionInfo {
  token: string;
  user: User;
}
