import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { DashboardComponent } from './dashboard/dashboard.component';
import { InventoryComponent } from './inventory/inventory.component';
import { ComicDetailComponent } from './comic-detail/comic-detail.component';
import { LoginComponent } from './login/login.component';
import { AuthCallbackComponent } from './auth-callback/auth-callback.component';
import { PendingApprovalComponent } from './pending-approval/pending-approval.component';
import { AdminGuard } from './admin.guard';
import { AuthGuard } from './auth.guard';
import { AccountProfileComponent } from './account-profile/account-profile.component';
import { OrderHistoryComponent } from './order-history/order-history.component';
import { AdminSalesComponent } from './admin-sales/admin-sales.component';
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
  { path: 'admin/users',   canActivate: [AdminGuard],
    loadComponent: () => import('./admin-users/admin-users.component').then(m => m.AdminUsersComponent) },
  { path: 'documentation',
    loadComponent: () => import('./documentation/documentation.component').then(m => m.DocumentationComponent) },
  { path: 'cart',          redirectTo: '/orders',            pathMatch: 'full' },
  { path: 'profile',       component: AccountProfileComponent, canActivate: [AuthGuard] },
  { path: 'admin/orders',  canActivate: [AdminGuard],
    loadComponent: () => import('./admin-orders/admin-orders.component').then(m => m.AdminOrdersComponent) },
  { path: 'orders',        component: OrderHistoryComponent, canActivate: [AuthGuard] },
  { path: 'sales',         component: AdminSalesComponent },
  { path: 'contact',
    loadComponent: () => import('./contact/contact.component').then(m => m.ContactComponent) },
  { path: 'trade',
    loadComponent: () => import('./trade-board/trade-board.component').then(m => m.TradeBoardComponent) },
];

@NgModule({
  imports: [ RouterModule.forRoot(routes, { anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }) ],
  exports: [ RouterModule ],
})
export class AppRoutingModule {}
