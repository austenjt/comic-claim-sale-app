import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  constructor(
    private msal: MsalService,
    private auth: AuthService,
    private router: Router,
  ) {}

  canActivate(): boolean {
    // Check MSAL cache — has the user completed Entra login?
    if (this.msal.instance.getAllAccounts().length === 0) {
      this.router.navigate(['/login']);
      return false;
    }
    // Check that we have an APPROVED user record loaded
    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/login']);
      return false;
    }
    return true;
  }
}
