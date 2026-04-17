import { APP_INITIALIZER, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import {
  MSAL_GUARD_CONFIG,
  MSAL_INSTANCE,
  MSAL_INTERCEPTOR_CONFIG,
  MsalBroadcastService,
  MsalGuard,
  MsalService,
} from '@azure/msal-angular';

import { ConfigService } from './config.service';
import { AuthInterceptor } from './auth.interceptor';
import { HttpLoggingInterceptor } from './http-logging.interceptor';
import { msalGuardConfig, msalInstanceFactory, msalInterceptorConfig } from './auth.config';

import { AppRoutingModule } from './app-routing.module';

import { AppComponent } from './app.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { ComicDetailComponent } from './comic-detail/comic-detail.component';
import { InventoryComponent } from './inventory/inventory.component';
import { ComicSearchComponent } from './comic-search/comic-search.component';
import { MessagesComponent } from './messages/messages.component';
import { StandaloneListComponent } from './standalone-list/standalone-list.component';
import { LoadGoCollectFormComponent } from './load-gocollect-form/load-gocollect-form.component';
import { LoginComponent } from './login/login.component';
import { AuthCallbackComponent } from './auth-callback/auth-callback.component';
import { PendingApprovalComponent } from './pending-approval/pending-approval.component';
import { AdminUsersComponent } from './admin-users/admin-users.component';
import { DocumentationComponent } from './documentation/documentation.component';
import { CartComponent } from './cart/cart.component';
import { AdminOrdersComponent } from './admin-orders/admin-orders.component';
import { AccountProfileComponent } from './account-profile/account-profile.component';
import { OrderHistoryComponent } from './order-history/order-history.component';
import { AdminSalesComponent } from './admin-sales/admin-sales.component';
import { ContactComponent } from './contact/contact.component';
import { SetDetailComponent } from './set-detail/set-detail.component';
import { DashboardHeaderComponent } from './dashboard-header/dashboard-header.component';
import { PaginationBarComponent } from './pagination-bar/pagination-bar.component';

@NgModule({
    declarations: [
        AppComponent,
        DashboardComponent,
        InventoryComponent,
        ComicDetailComponent,
        MessagesComponent,
        ComicSearchComponent,
        LoadGoCollectFormComponent,
        LoginComponent,
        AuthCallbackComponent,
        PendingApprovalComponent,
        AdminUsersComponent,
        DocumentationComponent,
        CartComponent,
        AdminOrdersComponent,
        AccountProfileComponent,
        OrderHistoryComponent,
        AdminSalesComponent,
        ContactComponent,
        SetDetailComponent,
        DashboardHeaderComponent,
        PaginationBarComponent,
    ],
    bootstrap: [AppComponent],
    imports: [
        BrowserModule,
        FormsModule,
        AppRoutingModule,
        StandaloneListComponent,
    ],
    providers: [
        // Load app config (biddingMode, awardModeEnabled, etc.) before any component renders
        {
            provide: APP_INITIALIZER,
            useFactory: (config: ConfigService) => () => config.load(),
            deps: [ConfigService],
            multi: true,
        },

        // MSAL providers
        { provide: MSAL_INSTANCE,          useFactory: msalInstanceFactory },
        { provide: MSAL_GUARD_CONFIG,      useValue: msalGuardConfig },
        { provide: MSAL_INTERCEPTOR_CONFIG, useValue: msalInterceptorConfig },
        MsalService,
        MsalGuard,
        MsalBroadcastService,

        // HTTP interceptors (order matters: logging first, then Bearer token attachment)
        { provide: HTTP_INTERCEPTORS, useClass: HttpLoggingInterceptor, multi: true },
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor,        multi: true },

        provideHttpClient(withInterceptorsFromDi()),
    ],
})
export class AppModule {}
