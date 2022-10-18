import { Component } from '@angular/core';
@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
})
export class AppComponent {
  public appPages = [
    { title: 'Registered Ranches', url: '/ranch-list', icon: 'cube' },
    { title: 'Upload Ranch', url: '/ranch-upload', icon: 'cloud-upload' },
    { title: 'Library', url: '/library-page', icon: 'book' },
  ];
  constructor() {}
}
