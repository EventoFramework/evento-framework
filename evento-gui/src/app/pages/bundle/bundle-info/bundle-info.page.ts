import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {BundleService} from '../../../services/bundle.service';
import {NavController} from '@ionic/angular';

@Component({
  selector: 'app-bundle-info',
  templateUrl: './bundle-info.page.html',
  styleUrls: ['./bundle-info.page.scss'],
})
export class BundleInfoPage implements OnInit {
  bundleId: string;
  bundle;
  componentHandlers: any;
  components = [];

  constructor(private route: ActivatedRoute, private bundleService: BundleService, private navController: NavController) { }

  async ngOnInit() {
    this.bundleId = this.route.snapshot.params.identifier;
    this.bundle = await this.bundleService.find(this.bundleId);
    this.bundle.handlers.sort((a,b) => (a.componentName + a.handlerType).localeCompare(b.componentName + b.handlerType));
    this.componentHandlers = this.bundle.handlers.reduce((c, h) => {
      if(!c[h.componentName]){
        c[h.componentName] = [];
      }
      c[h.componentName].push(h);
      return c;
    }, {});
    const map = {};
    for(const h of this.bundle.handlers){
      map[h.componentName] = {name: h.componentName, type: h.componentType};
    }
    this.components = Object.values(map);
  }

  async unregister() {
    await this.bundleService.unregister(this.bundleId);
    await this.navController.navigateBack('/bundle-list');

  }

}
