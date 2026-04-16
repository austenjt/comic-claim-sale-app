import { Component, OnInit, HostListener } from '@angular/core';
import { Router } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { AuthService } from './auth.service';
import { LogService } from './log.service';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.css'],
    standalone: false
})
export class AppComponent implements OnInit {
  title = 'Lightning Comics Rocks';
  profileOpen = false;

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.header-user')) {
      this.profileOpen = false;
    }
  }

  constructor(
    public auth: AuthService,
    public logService: LogService,
    private msal: MsalService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    // handleRedirectObservable() MUST be called on every page load in redirect flow.
    // It clears the interaction_in_progress lock MSAL sets before navigating to Entra,
    // and processes the token response when returning from Entra's login page.
    this.msal.initialize().subscribe(() => {
      this.msal.handleRedirectObservable().subscribe({
        error: () => {
          // MSAL failed to process the redirect — most commonly Safari ITP clearing
          // sessionStorage during the round-trip, causing a state_not_found error.
          // Navigate to login so the user can try again.
          this.router.navigate(['/login']);
        },
        next: (result) => {
          if (result?.account) {
            this.msal.instance.setActiveAccount(result.account);
          }

          // If the user has an Entra account in cache, fetch their CosmosDB record
          if (this.msal.instance.getAllAccounts().length > 0) {
            this.auth.loadCurrentUser().subscribe({
              next: (user) => {
                if (user) {
                  // Only navigate away from auth-callback or login pages after loading
                  const url = this.router.url;
                  if (url.includes('auth-callback') || url === '/login' || url === '/') {
                    this.router.navigate(['/dashboard']);
                  }
                }
              },
              error: (err) => {
                // 403 = PENDING or SUSPENDED
                if (err?.status === 403 || err?.status === 202) {
                  this.router.navigate(['/pending-approval']);
                }
              }
            });
          }
        },
      });
    });
  }

  login(): void {
    this.auth.signIn().subscribe();
  }

  logout(): void {
    this.auth.signOut();
  }
}
