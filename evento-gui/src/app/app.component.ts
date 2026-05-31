import {Component} from '@angular/core';

@Component({
    selector: 'app-root',
    templateUrl: 'app.component.html',
    styleUrls: ['app.component.scss'],
    standalone: false
})
export class AppComponent {

  selectedTab = 'payload-catalog';
  constructor() {}
}
