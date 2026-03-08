import { Component } from '@angular/core';
import { UserService } from '../user.service';

@Component({
  selector: 'app-account-request',
  templateUrl: './account-request.component.html',
  styleUrls: ['./account-request.component.css']
})
export class AccountRequestComponent {
  name = '';
  email = '';
  address = '';
  phone = '';
  paymentNotes = '';
  submitted = false;
  error = '';
  loading = false;

  constructor(private userService: UserService) {}

  onSubmit() {
    this.error = '';
    this.loading = true;
    this.userService.register(this.name, this.email, this.address, this.phone, this.paymentNotes).subscribe({
      next: () => {
        this.submitted = true;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error || 'Registration failed. Please try again.';
        this.loading = false;
      }
    });
  }
}
