import { Component, OnInit } from '@angular/core';
import { AuthService } from '../auth.service';
import { UserService } from '../user.service';
import { User, ShippingAddress } from '../user';

@Component({
    selector: 'app-account-profile',
    templateUrl: './account-profile.component.html',
    styleUrls: ['./account-profile.component.css'],
    standalone: false
})
export class AccountProfileComponent implements OnInit {
  name = '';
  street1 = '';
  street2 = '';
  city = '';
  state = '';
  zip = '';
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

  constructor(public auth: AuthService, private userService: UserService) {}

  ngOnInit() {
    const user = this.auth.currentUser$.value;
    if (user) {
      this.name = user.name ?? '';
      this.street1 = user.shippingAddress?.street1 ?? '';
      this.street2 = user.shippingAddress?.street2 ?? '';
      this.city = user.shippingAddress?.city ?? '';
      this.state = user.shippingAddress?.state ?? '';
      this.zip = user.shippingAddress?.zip ?? '';
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
    const shippingAddress: ShippingAddress | null = (this.street1 || this.city || this.state || this.zip)
      ? { street1: this.street1, street2: this.street2 || undefined, city: this.city, state: this.state, zip: this.zip, phone: this.phone || undefined }
      : null;
    this.userService.updateProfile(
      this.name, shippingAddress, this.phone, this.notes, this.preferences,
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
