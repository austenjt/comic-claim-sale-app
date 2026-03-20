import { Component, OnInit } from '@angular/core';
import { AuthService } from '../auth.service';
import { UserService } from '../user.service';
import { User } from '../user';
import { ConfigService } from '../config.service';

@Component({
  selector: 'app-account-profile',
  templateUrl: './account-profile.component.html',
  styleUrls: ['./account-profile.component.css']
})
export class AccountProfileComponent implements OnInit {
  name = '';
  address = '';
  phone = '';
  notes = '';
  preferences = '';
  venmoHandle = '';
  paypalHandle = '';
  ebayUsername = '';
  cashAppHandle = '';

  saving = false;
  saveSuccess = false;
  saveError = '';

  constructor(public auth: AuthService, private userService: UserService, public configService: ConfigService) {}

  ngOnInit() {
    const user = this.auth.currentUser$.value;
    if (user) {
      this.name = user.name ?? '';
      this.address = user.address ?? '';
      this.phone = user.phone ?? '';
      this.notes = user.notes ?? '';
      this.preferences = user.preferences ?? '';
      this.venmoHandle = user.venmoHandle ?? '';
      this.paypalHandle = user.paypalHandle ?? '';
      this.ebayUsername = user.ebayUsername ?? '';
      this.cashAppHandle = user.cashAppHandle ?? '';
    }
  }

  save() {
    this.saving = true;
    this.saveSuccess = false;
    this.saveError = '';
    this.userService.updateProfile(
      this.name, this.address, this.phone, this.notes, this.preferences,
      this.venmoHandle, this.paypalHandle, this.ebayUsername, this.cashAppHandle
    ).subscribe({
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
