import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ConfigService } from '../config.service';

@Component({
    selector: 'app-contact',
    templateUrl: './contact.component.html',
    styleUrls: ['./contact.component.css'],
    standalone: false
})
export class ContactComponent {
  private readonly apiBase = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';

  name = '';
  email = '';
  message = '';

  sending = false;
  sent = false;
  sendError = '';

  constructor(private http: HttpClient, private configService: ConfigService) {}

  get emailEnabled(): boolean {
    return this.configService.emailEnabled;
  }

  submit() {
    this.sending = true;
    this.sent = false;
    this.sendError = '';
    this.http.post(`${this.apiBase}/contact`, { name: this.name, email: this.email, message: this.message }, { responseType: 'text' })
      .subscribe({
        next: () => {
          this.sending = false;
          this.sent = true;
          this.name = '';
          this.email = '';
          this.message = '';
        },
        error: () => {
          this.sending = false;
          this.sendError = 'Something went wrong. Please try again.';
        }
      });
  }
}
