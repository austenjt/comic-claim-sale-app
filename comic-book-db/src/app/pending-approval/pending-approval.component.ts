import { Component } from '@angular/core';
import { AuthService } from '../auth.service';

@Component({
    selector: 'app-pending-approval',
    templateUrl: './pending-approval.component.html',
    standalone: false
})
export class PendingApprovalComponent {
  constructor(private auth: AuthService) {}

  signOut(): void {
    this.auth.signOut();
  }
}
