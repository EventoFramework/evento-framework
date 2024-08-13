import { Component, OnInit } from '@angular/core';
import {ClusterStatusService} from "../../../../services/cluster-status.service";
import {ConsumerService} from "../../../../services/consumer.service";
import {JsonPipe, NgForOf, NgIf} from "@angular/common";
import {IonicModule, IonModal} from "@ionic/angular";
import {RouterLink} from "@angular/router";
import {TranslateModule} from "@ngx-translate/core";

@Component({
  selector: 'app-consumers',
  templateUrl: './consumers.component.html',
  styleUrls: ['./consumers.component.scss'],
  standalone: true,
  imports: [
    JsonPipe,
    IonicModule,
    NgForOf,
    NgIf,
    RouterLink,
    TranslateModule
  ]
})
export class ConsumersComponent  implements OnInit {

  protected consumers: any[];
  protected consumerState: any;

  constructor(private service: ConsumerService) { }

  ngOnInit() {
    this.service.findAllConsumers().then(c => this.consumers = c)
  }

  fetchConsumerState(c: any, modal: IonModal) {
    modal.dismiss();
    this.service.fetchConsumerState(c.consumerId).then(c =>{
      this.consumerState = c;
      modal.present();
    })
  }
}
