import {
  BrowserCacheLocation,
  InteractionType,
  IPublicClientApplication,
  LogLevel,
  PublicClientApplication,
} from '@azure/msal-browser';
import {
  MsalGuardConfiguration,
  MsalInterceptorConfiguration,
} from '@azure/msal-angular';

const clientId        = '5b837ae4-4513-4d73-9b4b-9a697a26f5ea';
const tenantSubdomain = 'lightningcomics';
const apiBase         = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';

/**
 * MSAL PublicClientApplication instance.
 *
 * Authority follows the Entra External ID CIAM URL format:
 *   https://{tenantSubdomain}.ciamlogin.com/{tenantSubdomain}.onmicrosoft.com
 * Note: CIAM does NOT accept the GUID tenant ID in the authority URL.
 */
export function msalInstanceFactory(): IPublicClientApplication {
  const authority   = `https://${tenantSubdomain}.ciamlogin.com/${tenantSubdomain}.onmicrosoft.com`;
  const redirectUri = `${window.location.origin}/auth-callback`;

  return new PublicClientApplication({
    auth: {
      clientId,
      authority,
      knownAuthorities: [`${tenantSubdomain}.ciamlogin.com`],
      redirectUri,
      postLogoutRedirectUri: window.location.origin,
      navigateToLoginRequestUrl: false,
    },
    cache: {
      cacheLocation: BrowserCacheLocation.LocalStorage,
      // Required for Safari (ITP) and Firefox (ETP): sessionStorage is cleared during
      // cross-origin redirects, so MSAL must also persist auth state in a cookie.
      storeAuthStateInCookie: true,
    },
    system: {
      loggerOptions: {
        logLevel: LogLevel.Warning,
        piiLoggingEnabled: false,
      },
    },
  });
}

/** MSAL Guard — redirect interaction for sign-in. */
export const msalGuardConfig: MsalGuardConfiguration = {
  interactionType: InteractionType.Redirect,
  authRequest: {
    scopes: [`${clientId}/.default`],
  },
};

/**
 * MSAL Interceptor — attaches Bearer tokens to requests to the Lightning Comics API.
 * HashLocationStrategy means redirectUri will include a # fragment; MSAL handles this.
 */
export const msalInterceptorConfig: MsalInterceptorConfiguration = {
  interactionType: InteractionType.Redirect,
  protectedResourceMap: new Map([
    [apiBase, [`${clientId}/.default`]],
  ]),
};

export { clientId, apiBase };
