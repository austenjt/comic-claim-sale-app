import { Component, OnInit } from '@angular/core';
import { Title, Meta } from '@angular/platform-browser';

@Component({
    selector: 'app-documentation',
    templateUrl: './documentation.component.html',
    styleUrls: ['./documentation.component.css'],
    standalone: false
})
export class DocumentationComponent implements OnInit {
  constructor(private title: Title, private meta: Meta) {}

  ngOnInit(): void {
    this.title.setTitle('How It Works — Lightning Comics PDX');
    this.meta.updateTag({ name: 'description', content: 'Learn how to browse, claim, and purchase graded and raw comics at Lightning Comics PDX in Oregon City, Oregon.' });
  }
}
