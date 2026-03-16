import { NgModule, APP_INITIALIZER } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { ConfigService } from './config.service';
import { HttpLoggingInterceptor } from './http-logging.interceptor';
import { AuthInterceptor } from './auth.interceptor';

import { AppRoutingModule } from './app-routing.module';
import { AgGridModule } from 'ag-grid-angular';

import { AppComponent } from './app.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { ComicDetailComponent } from './comic-detail/comic-detail.component';
import { ComicsComponent } from './comics/comics.component';
import { ComicSearchComponent } from './comic-search/comic-search.component';
import { MessagesComponent } from './messages/messages.component';
import { StandaloneListComponent } from './standalone-list/standalone-list.component';
import { LoadGoCollectFormComponent } from './load-gocollect-form/load-gocollect-form.component';
import { LoginComponent } from './login/login.component';
import { AccountRequestComponent } from './account-request/account-request.component';
import { AdminUsersComponent } from './admin-users/admin-users.component';
import { HowItWorksComponent } from './how-it-works/how-it-works.component';
import { CartComponent } from './cart/cart.component';
import { AdminOrdersComponent } from './admin-orders/admin-orders.component';
import { AccountProfileComponent } from './account-profile/account-profile.component';
import { OrderHistoryComponent } from './order-history/order-history.component';
import { AdminSalesComponent } from './admin-sales/admin-sales.component';
import { ContactComponent } from './contact/contact.component';

@NgModule({
  declarations: [
    AppComponent,
    DashboardComponent,
    ComicsComponent,
    ComicDetailComponent,
    MessagesComponent,
    ComicSearchComponent,
    LoadGoCollectFormComponent,
    LoginComponent,
    AccountRequestComponent,
    AdminUsersComponent,
    HowItWorksComponent,
    CartComponent,
    AdminOrdersComponent,
    AccountProfileComponent,
    OrderHistoryComponent,
    AdminSalesComponent,
    ContactComponent
  ],
  imports: [
    BrowserModule,
    FormsModule,
    AppRoutingModule,
    HttpClientModule,
    StandaloneListComponent
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: HttpLoggingInterceptor, multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
    {
      provide: APP_INITIALIZER,
      useFactory: (configService: ConfigService) => () => configService.load(),
      deps: [ConfigService],
      multi: true
    }
  ],
  bootstrap: [ AppComponent ]
})
export class AppModule { }
