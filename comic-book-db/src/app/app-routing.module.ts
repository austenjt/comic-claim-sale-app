import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { DashboardComponent } from './dashboard/dashboard.component';
import { InventoryComponent } from './inventory/inventory.component';
import { ComicDetailComponent } from './comic-detail/comic-detail.component';
import { LoginComponent } from './login/login.component';
import { AuthCallbackComponent } from './auth-callback/auth-callback.component';
import { PendingApprovalComponent } from './pending-approval/pending-approval.component';
import { AdminUsersComponent } from './admin-users/admin-users.component';
import { AdminGuard } from './admin.guard';
import { DocumentationComponent } from './documentation/documentation.component';
import { CartComponent } from './cart/cart.component';
import { AdminOrdersComponent } from './admin-orders/admin-orders.component';
import { AuthGuard } from './auth.guard';
import { AccountProfileComponent } from './account-profile/account-profile.component';
import { OrderHistoryComponent } from './order-history/order-history.component';
import { AdminSalesComponent } from './admin-sales/admin-sales.component';
import { ContactComponent } from './contact/contact.component';
import { SetDetailComponent } from './set-detail/set-detail.component';

const routes: Routes = [
  { path: '',               redirectTo: '/dashboard', pathMatch: 'full' },
  { path: 'dashboard',     component: DashboardComponent },
  { path: 'detail/:id',    component: ComicDetailComponent },
  { path: 'set/:id',       component: SetDetailComponent },
  { path: 'inventory',     component: InventoryComponent },
  { path: 'login',         component: LoginComponent },
  { path: 'auth-callback', component: AuthCallbackComponent },
  { path: 'pending-approval', component: PendingApprovalComponent },
  { path: 'admin/users',   component: AdminUsersComponent,   canActivate: [AdminGuard] },
  { path: 'documentation',  component: DocumentationComponent },
  { path: 'cart',          component: CartComponent,         canActivate: [AuthGuard] },
  { path: 'profile',       component: AccountProfileComponent, canActivate: [AuthGuard] },
  { path: 'admin/orders',  component: AdminOrdersComponent,  canActivate: [AdminGuard] },
  { path: 'orders',        component: OrderHistoryComponent, canActivate: [AuthGuard] },
  { path: 'sales',         component: AdminSalesComponent },
  { path: 'contact',       component: ContactComponent },
];

@NgModule({
  imports: [ RouterModule.forRoot(routes) ],
  exports: [ RouterModule ],
})
export class AppRoutingModule {}
