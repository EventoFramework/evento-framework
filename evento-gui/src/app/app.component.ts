import { Component } from '@angular/core';
@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
})
export class AppComponent {
  public appPages = [
    { title: 'evento.application.map', url: '/application-map', icon: 'apps' },
    { title: 'evento.application.flows', url: '/application-flows', icon: 'git-compare' },
    { title: 'evento.cluster.status', url: '/cluster-status', icon: 'layers' },
    { title: 'evento.registered.bundles', url: '/bundle-list', icon: 'cube' },
    { title: 'evento.library', url: '/library-page', icon: 'book' },
  ];
  constructor() {}
}
