import { Component, OnInit } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'Lightning Comics PDX';

  constructor(
    public auth: AuthService,
    public toastService: ToastService,
    private router: Router
  ) {}

  ngOnInit() {
    this.auth.loadSession();
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd)
    ).subscribe(() => this.toastService.clearAll());
  }

  logout() {
    this.auth.logout();
  }
}
