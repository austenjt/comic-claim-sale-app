import { Component } from '@angular/core';

/**
 * Landing page for the post-redirect response from Entra External ID.
 * The redirect is processed by AppComponent.ngOnInit via handleRedirectObservable(),
 * which also handles navigation to the dashboard or pending-approval page on completion.
 * This component exists solely as a visual placeholder during that processing.
 */
@Component({
    selector: 'app-auth-callback',
    template: `<p style="text-align:center; margin-top: 4rem;">Completing sign-in…</p>`,
    standalone: false
})
export class AuthCallbackComponent {}
