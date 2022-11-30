import { Component } from '@angular/core';
@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
})
export class AppComponent {
  public appPages = [
    { title: 'application.map.title', url: '/application-map', icon: 'apps' },
    { title: 'application.flows.title', url: '/application-flows/all', icon: 'git-compare' },
    { title: 'cluster.status', url: '/cluster-status', icon: 'layers' },
    { title: 'bundle.list.title', url: '/bundle-list', icon: 'cube' },
    { title: 'library.title', url: '/library-page', icon: 'book' },
  ];
  constructor() {}
}
