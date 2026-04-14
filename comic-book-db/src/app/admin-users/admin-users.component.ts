import { Component, OnInit } from '@angular/core';
import { UserService } from '../user.service';
import { User } from '../user';

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

  constructor(private userService: UserService) {}

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
}
