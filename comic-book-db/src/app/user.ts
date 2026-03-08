export interface User {
  id: string;
  name: string;
  email: string;
  address?: string;
  phone?: string;
  paymentNotes?: string;
  status: 'PENDING' | 'APPROVED' | 'SUSPENDED';
  isAdmin: boolean;
  createdDate?: string;
  approvedDate?: string;
}

export interface SessionInfo {
  token: string;
  user: User;
}
