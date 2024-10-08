import {Component, OnInit} from '@angular/core';
import {BundleService} from '../../../services/bundle.service';
import {stringToColour} from '../../../services/utils';

@Component({
  selector: 'app-bundle-list',
  templateUrl: './bundle-list.page.html',
  styleUrls: ['./bundle-list.page.scss'],
})
export class BundleListPage implements OnInit {
  domains = new Set<any>();
  selectedDomains = {};
  search = '';

  bundles = [];
  allBundles = [];

  constructor(private bundleService: BundleService) {
  }

  async ionViewWillEnter(){
    this.allBundles = await this.bundleService.findAll();
    const domains = new Set();
    for (const b of this.allBundles) {
      b.color = stringToColour(b.id);
      b.domains = (b.domains?.split(',') || []);
      for(const d of b.domains){
        domains.add(d);
      }
    }
    this.domains = domains;

    this.checkFilters();
  }
  async ngOnInit() {

  }

  public checkFilters() {
    const hasSelectedDomains = Object.values(this.selectedDomains).reduce((a, b) => a || b, false);
    this.bundles = this.allBundles.filter(b => {
      let match = true;
      if (this.search.length > 0) {
        match = match && (b.id.toLowerCase().includes(this.search.toLowerCase())
          || b.description?.toLowerCase().includes(this.search.toLowerCase()));
      }
      if (hasSelectedDomains)
        {match = match && b.domains.filter(h => this.selectedDomains[h]).length > 0;}
      return match;
    });
  }
}
