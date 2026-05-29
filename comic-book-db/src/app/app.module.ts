import { APP_INITIALIZER, ErrorHandler, NgModule } from '@angular/core';
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
import { GlobalErrorHandler } from './telemetry.service';
import { msalGuardConfig, msalInstanceFactory, msalInterceptorConfig } from './auth.config';

import { AppRoutingModule } from './app-routing.module';

import { AppComponent } from './app.component';
import { SellingComponent } from './selling/selling.component';
import { ComicDetailComponent } from './comic-detail/comic-detail.component';
import { InventoryComponent } from './inventory/inventory.component';
import { ComicSearchComponent } from './comic-search/comic-search.component';
import { StandaloneListComponent } from './standalone-list/standalone-list.component';
import { LoadGoCollectFormComponent } from './load-gocollect-form/load-gocollect-form.component';
import { LoginComponent } from './login/login.component';
import { AuthCallbackComponent } from './auth-callback/auth-callback.component';
import { PendingApprovalComponent } from './pending-approval/pending-approval.component';
import { CartComponent } from './cart/cart.component';
import { AccountProfileComponent } from './account-profile/account-profile.component';
import { OrderHistoryComponent } from './order-history/order-history.component';
import { AdminSalesComponent } from './admin-sales/admin-sales.component';
import { SetDetailComponent } from './set-detail/set-detail.component';
import { SellingHeaderComponent } from './selling-header/selling-header.component';
import { SellingCardComponent } from './selling-card/selling-card.component';
import { PaginationBarComponent } from './pagination-bar/pagination-bar.component';
import { SalesModalComponent } from './sales-modal/sales-modal.component';
import { SquarePaymentModalComponent } from './square-payment-modal/square-payment-modal.component';
import { ImageCaptureModalComponent } from './image-capture-modal/image-capture-modal.component';
import { ShippingModalComponent } from './shipping-modal/shipping-modal.component';

@NgModule({
    declarations: [
        AppComponent,
        SellingComponent,
        InventoryComponent,
        ComicDetailComponent,
        ComicSearchComponent,
        LoadGoCollectFormComponent,
        LoginComponent,
        AuthCallbackComponent,
        PendingApprovalComponent,
        CartComponent,
        AccountProfileComponent,
        OrderHistoryComponent,
        AdminSalesComponent,
        SetDetailComponent,
        SellingHeaderComponent,
        SellingCardComponent,
        PaginationBarComponent,
        SalesModalComponent,
        SquarePaymentModalComponent,
        ImageCaptureModalComponent,
        ShippingModalComponent,
    ],
    bootstrap: [AppComponent],
    imports: [
        BrowserModule,
        FormsModule,
        AppRoutingModule,
        StandaloneListComponent,
    ],
    providers: [
        // Load app config (awardModeEnabled, emailEnabled, etc.) before any component renders
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

        // Global error handler — routes uncaught errors through TelemetryService
        { provide: ErrorHandler, useClass: GlobalErrorHandler },

        // HTTP interceptors (order matters: logging first, then Bearer token attachment)
        { provide: HTTP_INTERCEPTORS, useClass: HttpLoggingInterceptor, multi: true },
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor,        multi: true },

        provideHttpClient(withInterceptorsFromDi()),
    ],
})
export class AppModule {}
