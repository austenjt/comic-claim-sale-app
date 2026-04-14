import { Component, OnInit } from '@angular/core';
import { Title, Meta } from '@angular/platform-browser';

@Component({
    selector: 'app-how-it-works',
    templateUrl: './how-it-works.component.html',
    styleUrls: ['./how-it-works.component.css'],
    standalone: false
})
export class HowItWorksComponent implements OnInit {
  constructor(private title: Title, private meta: Meta) {}

  ngOnInit(): void {
    this.title.setTitle('How It Works — Lightning Comics PDX');
    this.meta.updateTag({ name: 'description', content: 'Learn how to browse, claim, and purchase graded and raw comics at Lightning Comics PDX in Oregon City, Oregon.' });
  }
}
