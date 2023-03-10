import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from "@angular/router";
import {CatalogService} from "../../../../services/catalog.service";

@Component({
  selector: 'app-payload-info',
  templateUrl: './payload-info.page.html',
  styleUrls: ['./payload-info.page.scss'],
})
export class PayloadInfoPage implements OnInit {
  payload;
  isEditing = false;

  constructor(private route: ActivatedRoute,
              private catalogService: CatalogService) {
  }

  ngOnInit() {
    this.catalogService.findByName(this.route.snapshot.paramMap.get('identifier')).then(r => {
      r.subscribers = r.subscribers?.split(',') || [];
      r.invokers = r.invokers?.split(',') || [];
      r.returnedBy = r.returnedBy?.split(',') || [];
      this.payload = r;
      console.log(this.payload)
    });
  }

  save() {
    this.isEditing = false;
  }
}
