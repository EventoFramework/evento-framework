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
      this.fields = Object.keys(r.jsonSchema || {});
      this.payload = r;
    });
  }

  async save() {
    this.isEditing = false;
    await this.catalogService.updatePayload(this.payload.name, this.payload.description, this.payload.detail, this.payload.domain);
  }
}
