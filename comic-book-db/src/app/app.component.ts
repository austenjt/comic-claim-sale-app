import { Component, OnInit } from '@angular/core';
import { AuthService } from './auth.service';
import { LogService } from './log.service';
import { ConfigService } from './config.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'Lightning Comics Rocks';

  constructor(
    public auth: AuthService,
    public logService: LogService,
    public configService: ConfigService
  ) {}

  ngOnInit() {
    this.auth.loadSession();
  }

  logout() {
    this.auth.logout();
  }
}
