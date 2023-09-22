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
  environmentKeys: string[] = [];
  vmOptionsKeys: string[] = [];

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
    this.environmentKeys = Object.keys(this.bundle.environment);
    this.vmOptionsKeys = Object.keys(this.bundle.vmOptions);
  }

  putEnv(key, value) {
    this.bundleService.putEnv(this.bundleId, key, value).finally();
    this.bundle.environment[key] = value;
    this.environmentKeys = Object.keys(this.bundle.environment);
  }
  removeEnv(key) {
    this.bundleService.removeEnv(this.bundleId, key).finally();
    delete this.bundle.environment[key];
    this.environmentKeys = Object.keys(this.bundle.environment);
  }
  putVmOption(key, value) {
    this.bundleService.putVmOption(this.bundleId, key, value).finally();
    this.bundle.vmOptions[key] = value;
    this.vmOptionsKeys = Object.keys(this.bundle.vmOptionsKeys);
  }
  removeVmOption(key) {
    this.bundleService.removeVmOption(this.bundleId, key).finally();
    delete this.bundle.vmOptions[key];
    this.vmOptionsKeys = Object.keys(this.bundle.vmOptionsKeys);
  }

  async unregister() {
    await this.bundleService.unregister(this.bundleId);
    await this.navController.navigateBack('/bundle-list');

  }

}
