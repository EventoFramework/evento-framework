import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {CatalogService} from '../../../../services/catalog.service';
import {RepositoryService} from '../../../../services/repository.service';

@Component({
    selector: 'app-payload-info',
    templateUrl: './payload-info.page.html',
    styleUrls: ['./payload-info.page.scss'],
    standalone: false
})
export class PayloadInfoPage implements OnInit {
  payload;

  fields = [];
  schema = {};

  constructor(private route: ActivatedRoute,
              private catalogService: CatalogService,
              private repository: RepositoryService) {
  }

  async ngOnInit() {
    await this.repository.whenReady();
    this.catalogService.findPayloadByName(this.route.snapshot.paramMap.get('identifier')).then(r => {
      const parseRef = s => {
        const parts = s.split('$$$');
        return {
          name: parts[0],
          type: parts[1],
          path: parts[2],
          line: parts[3],
          bundleId: parts[4],
          repoLink: this.repository.link(parts[4], parts[2], parts[3]),
        };
      };
      r.subscribers = r.subscribers?.split(',')?.map(parseRef) || [];
      r.invokers = r.invokers?.split(',')?.map(parseRef) || [];
      r.returnedBy = r.returnedBy?.split(',')?.map(parseRef) || [];
      r.usedBy = r.usedBy?.split(',')?.map(parseRef) || [];
      r.repoLink = this.repository.link(r.bundleId, r.path, r.line);
      r.jsonSchema = JSON.parse(r.jsonSchema);
      if (r.validJsonSchema) {
        this.schema = this.flattenJSON(r.jsonSchema.properties);
        this.fields = Object.keys(this.schema);
      } else {
        this.schema = r.jsonSchema;
        this.fields = Object.keys(this.schema || {});
      }
      this.payload = r;
    });
  }

  private flattenJSON(obj = {}, res = {}, extraKey = '') {
    for (const key in obj) {
      if (typeof obj[key] !== 'object') {
        res[extraKey + key] = obj[key];
      } else {
        this.flattenJSON(obj[key], res, `${extraKey}${key}.`);
      }
    }
    return res;
  };
}
