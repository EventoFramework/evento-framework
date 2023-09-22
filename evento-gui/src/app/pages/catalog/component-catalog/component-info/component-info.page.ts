import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {CatalogService} from '../../../../services/catalog.service';
import {stringToColour} from '../../../../services/utils';

@Component({
  selector: 'app-component-info',
  templateUrl: './component-info.page.html',
  styleUrls: ['./component-info.page.scss'],
})
export class ComponentInfoPage implements OnInit {

  component;

  constructor(private route: ActivatedRoute,
              private catalogService: CatalogService) {
  }

  ngOnInit() {
    this.catalogService.findComponentByName(this.route.snapshot.paramMap.get('identifier')).then(r => {
      r.bundleColor = stringToColour(r.bundleId);
      r.domains = new Set();
      const invocations = {};
      for (const h of r.handlers) {
        r.domains.add(h.handledPayload.domain);
        for(const i of Object.values(h.invocations) as any[]){
          invocations[i.name] = i;
        }
      }
      r.invocations = Object.values(invocations);
      this.component = r;
    });
  }
}
