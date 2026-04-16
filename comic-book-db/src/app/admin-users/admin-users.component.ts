import { Component, OnInit } from '@angular/core';
import { UserService } from '../user.service';
import { User } from '../user';
import { CartService } from '../cart.service';

@Component({
    selector: 'app-admin-users',
    templateUrl: './admin-users.component.html',
    styleUrls: ['./admin-users.component.css'],
    standalone: false
})
export class AdminUsersComponent implements OnInit {
  pendingUsers: User[] = [];
  existingUsers: User[] = [];
  loading = false;
  error = '';

  resetConfirmText = '';
  resetInProgress = false;
  resetSuccess = '';
  resetError = '';

  constructor(private userService: UserService, private cartService: CartService) {}

  ngOnInit() {
    this.loadAll();
  }

  loadAll() {
    this.loading = true;
    this.userService.getPendingUsers().subscribe({
      next: users => { this.pendingUsers = users; },
      error: () => { this.error = 'Failed to load pending users.'; }
    });
    this.userService.getApprovedUsers().subscribe({
      next: users => { this.existingUsers = users; this.loading = false; },
      error: () => { this.error = 'Failed to load approved users.'; this.loading = false; }
    });
  }

  approve(user: User) {
    this.userService.approveUser(user.id).subscribe({
      next: () => {
        this.pendingUsers = this.pendingUsers.filter(u => u.id !== user.id);
        this.userService.getApprovedUsers().subscribe({
          next: users => { this.existingUsers = users; }
        });
      },
      error: () => { this.error = 'Failed to approve user.'; }
    });
  }

  suspend(user: User) {
    this.userService.suspendUser(user.id).subscribe({
      next: () => this.userService.getApprovedUsers().subscribe({
        next: users => { this.existingUsers = users; }
      }),
      error: () => { this.error = 'Failed to suspend user.'; }
    });
  }

  reactivate(user: User) {
    this.userService.reactivateUser(user.id).subscribe({
      next: () => this.userService.getApprovedUsers().subscribe({
        next: users => { this.existingUsers = users; }
      }),
      error: () => { this.error = 'Failed to reactivate user.'; }
    });
  }

  resetDatabase() {
    this.resetInProgress = true;
    this.resetSuccess = '';
    this.resetError = '';
    this.cartService.resetDatabase().subscribe({
      next: () => {
        this.resetSuccess = 'Database reset complete. All comics, images, carts, discounts, and sessions have been cleared.';
        this.resetConfirmText = '';
        this.resetInProgress = false;
      },
      error: () => {
        this.resetError = 'Reset failed. Check the server logs.';
        this.resetInProgress = false;
      }
    });
  }
}
