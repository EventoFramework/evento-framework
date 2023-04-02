import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {CatalogService} from '../../../../services/catalog.service';

@Component({
  selector: 'app-payload-info',
  templateUrl: './payload-info.page.html',
  styleUrls: ['./payload-info.page.scss'],
})
export class PayloadInfoPage implements OnInit {
  payload;
  isEditing = false;

  fields = [];
  schema = {};

  constructor(private route: ActivatedRoute,
              private catalogService: CatalogService) {
  }

  ngOnInit() {
    this.catalogService.findPayloadByName(this.route.snapshot.paramMap.get('identifier')).then(r => {
      r.subscribers = r.subscribers?.split(',')?.map(s => {
        const parts = s.split(':');
        return {
          name: parts[0],
          type: parts[1]
        };
      }) || [];
      r.invokers = r.invokers?.split(',')?.map(s => {
        const parts = s.split(':');
        return {
          name: parts[0],
          type: parts[1]
        };
      }) || [];
      r.returnedBy = r.returnedBy?.split(',')?.map(s => {
        const parts = s.split(':');
        return {
          name: parts[0],
          type: parts[1]
        };
      }) || [];
      r.usedBy = r.usedBy?.split(',')?.map(s => {
        const parts = s.split(':');
        return {
          name: parts[0],
          type: parts[1]
        };
      }) || [];
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

  async save() {
    this.isEditing = false;
    await this.catalogService.updatePayload(this.payload.name, this.payload.description, this.payload.detail, this.payload.domain);
  }
}
