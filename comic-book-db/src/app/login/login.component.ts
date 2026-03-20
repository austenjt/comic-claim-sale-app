import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  email = '';
  pin = '';
  error = '';
  suspended = false;
  loading = false;

  constructor(private auth: AuthService, private router: Router) {
    try {
      this.email = localStorage.getItem('lastLoginEmail') ?? '';
    } catch {
      this.email = '';
    }
  }

  onSubmit() {
    this.error = '';
    this.suspended = false;
    this.loading = true;
    this.auth.login(this.email, this.pin).subscribe({
      next: () => {
        try { localStorage.setItem('lastLoginEmail', this.email); } catch { /* ignore */ }
        this.loading = false;
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 403) {
          this.suspended = true;
        } else {
          this.error = 'Invalid email or PIN. Please try again.';
        }
      }
    });
  }
}
