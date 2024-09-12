import {Component, OnInit} from '@angular/core';
import {CatalogService} from '../../../services/catalog.service';

@Component({
  selector: 'app-payload-catalog',
  templateUrl: './payload-catalog-page.component.html',
  styleUrls: ['./payload-catalog-page.component.scss'],
})
export class PayloadCatalogPage implements OnInit {

  types = new Set<any>();
  domains = new Set<any>();
  components = new Set<any>();

  payloads = [];
  allPayloads = [];
  selectedTypes = {};
  selectedComponents = {};
  selectedDomains = {};
  search = '';

  constructor(private libraryService: CatalogService) {
  }

  async ionVIewWillEnter() {
    this.allPayloads = await this.libraryService.findAllPayload();
    const types = new Set();
    const components = new Set();
    const domains = new Set();
    for (const msg of this.allPayloads) {
      types.add(msg.type);
      if (msg.domain) {
        domains.add(msg.domain);
      }
      msg.subscribers = (msg.subscribers || '').split(',');
      for (const h of msg.subscribers) {
        if (h) {
          components.add(h);
        }
      }
    }

    this.types = types;
    this.domains = domains;
    this.components = components;


    this.checkFilters();
  }

  async ngOnInit() {

  }

  public checkFilters() {
    const hasSelectedTypes = Object.values(this.selectedTypes).reduce((a, b) => a || b, false);
    const hasSelectedComponents = Object.values(this.selectedComponents).reduce((a, b) => a || b, false);
    const hasSelectedDomains = Object.values(this.selectedDomains).reduce((a, b) => a || b, false);
    this.payloads = this.allPayloads.filter(p => {
      let match = true;
      if (this.search.length > 0) {
        match = match && (p.name.toLowerCase().includes(this.search.toLowerCase())
          || p.description?.toLowerCase().includes(this.search.toLowerCase()));
      }
      if (hasSelectedTypes) {
        match = match && this.selectedTypes[p.type];
      }
      if (hasSelectedComponents) {
        match = match && p.subscribers.filter(h => this.selectedComponents[h.componentName]).length > 0;
      }
      if (hasSelectedDomains) {
        match = match && this.selectedDomains[p.domain];
      }
      return match;
    });
  }
}
