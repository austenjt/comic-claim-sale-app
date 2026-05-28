import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
    selector: 'app-login',
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.css'],
    standalone: false
})
export class LoginComponent implements OnInit {
  loading = false;
  error = '';

  constructor(private auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    if (this.auth.isAuthenticated && this.auth.isLoggedIn()) {
      this.router.navigate(['/selling']);
    }
  }

  signIn(): void {
    this.loading = true;
    this.error = '';
    this.auth.signIn().subscribe({
      error: () => {
        this.loading = false;
        this.error = 'Sign-in failed. Please try again.';
      }
    });
  }
}
