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
        for(const i of Object.keys(h.invocations) as any[]){
          if(!invocations[h.invocations[i].name]){
            invocations[h.invocations[i].name] = {...h.invocations[i], lines: []}
          }
          invocations[h.invocations[i].name].lines.push(i);
        }
      }
      r.invocations = Object.values(invocations);
      this.component = r;
    });
  }
}
