import { Component, OnInit } from '@angular/core';
import { UserService } from '../user.service';
import { User } from '../user';

@Component({
  selector: 'app-admin-users',
  templateUrl: './admin-users.component.html',
  styleUrls: ['./admin-users.component.css']
})
export class AdminUsersComponent implements OnInit {
  pendingUsers: User[] = [];
  existingUsers: User[] = [];
  loading = false;
  error = '';
  approvedPin: string | null = null;
  approvedUserName = '';

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
      next: resp => {
        this.approvedPin = resp.pin;
        this.approvedUserName = user.name;
        this.pendingUsers = this.pendingUsers.filter(u => u.id !== user.id);
        this.userService.getApprovedUsers().subscribe({
          next: users => { this.existingUsers = users; }
        });
      },
      error: () => {
        this.error = 'Failed to approve user.';
      }
    });
  }

  resetPin(user: User) {
    this.userService.resetPin(user.id).subscribe({
      next: resp => {
        this.approvedPin = resp.pin;
        this.approvedUserName = user.name;
      },
      error: () => { this.error = 'Failed to reset PIN.'; }
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

  dismissPin() {
    this.approvedPin = null;
    this.approvedUserName = '';
  }
}
