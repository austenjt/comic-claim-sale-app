import { Component, OnInit } from '@angular/core';
import { AuthService } from '../auth.service';
import { UserService } from '../user.service';
import { User } from '../user';

@Component({
  selector: 'app-account-profile',
  templateUrl: './account-profile.component.html',
  styleUrls: ['./account-profile.component.css']
})
export class AccountProfileComponent implements OnInit {
  name = '';
  address = '';
  phone = '';
  paymentNotes = '';

  saving = false;
  saveSuccess = false;
  saveError = '';

  constructor(public auth: AuthService, private userService: UserService) {}

  ngOnInit() {
    const user = this.auth.currentUser$.value;
    if (user) {
      this.name = user.name ?? '';
      this.address = user.address ?? '';
      this.phone = user.phone ?? '';
      this.paymentNotes = user.paymentNotes ?? '';
    }
  }

  save() {
    this.saving = true;
    this.saveSuccess = false;
    this.saveError = '';
    this.userService.updateProfile(this.name, this.address, this.phone, this.paymentNotes).subscribe({
      next: (updated: User) => {
        this.saving = false;
        this.saveSuccess = true;
        this.auth.currentUser$.next(updated);
      },
      error: () => {
        this.saving = false;
        this.saveError = 'Failed to save changes. Please try again.';
      }
    });
  }
}
