import {Component, OnInit} from '@angular/core';
import {CatalogService} from '../../../services/catalog.service';

@Component({
  selector: 'app-component-catalog',
  templateUrl: './component-catalog.page.html',
  styleUrls: ['./component-catalog.page.scss'],
})
export class ComponentCatalogPage implements OnInit {

  types = new Set();
  bundles = new Set();
  domains = new Set();
  selectedTypes = {};
  selectedBundles= {};
  selectedDomains = {};
  search = '';

  components = [];
  allComponents = [];

  constructor(private catalogService: CatalogService) {
  }

  async ngOnInit() {
    this.allComponents = await this.catalogService.findAllComponent();
    this.types = new Set();
    this.bundles = new Set();
    this.domains = new Set();
    for (const msg of this.allComponents) {
      msg.domains = (msg.domains?.split(',') || []);
      this.types.add(msg.componentType);
      this.bundles.add(msg.bundleId);
      for(const d of msg.domains){
        this.domains.add(d);
      }
    }


    this.checkFilters();
  }

  public checkFilters() {
    const hasSelectedTypes = Object.values(this.selectedTypes).reduce((a, b) => a || b, false);
    const hasSelectedBundles = Object.values(this.selectedBundles).reduce((a, b) => a || b, false);
    const hasSelectedDomains = Object.values(this.selectedDomains).reduce((a, b) => a || b, false);
    this.components = this.allComponents.filter(c => {
      let match = true;
      if (this.search.length > 0) {
        match = match && (c.componentName.toLowerCase().includes(this.search.toLowerCase())
          || c.description?.toLowerCase().includes(this.search.toLowerCase()));
      }
      if (hasSelectedTypes)
        {match = match && this.selectedTypes[c.componentType];}
      if (hasSelectedDomains)
        {match = match && c.domains.filter(h => this.selectedDomains[h]).length > 0;}
      if (hasSelectedBundles)
        {match = match && this.selectedBundles[c.bundleId];}
      return match;
    });
  }
}
