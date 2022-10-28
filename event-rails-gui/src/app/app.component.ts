import { Component } from '@angular/core';
@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
})
export class AppComponent {
  public appPages = [
    { title: 'Application Map', url: '/application-map', icon: 'apps' },
    { title: 'Cluster Status', url: '/cluster-status', icon: 'layers' },
    { title: 'Registered Bundles', url: '/bundle-list', icon: 'cube' },
    { title: 'Library', url: '/library-page', icon: 'book' },
  ];
  constructor() {}
}
