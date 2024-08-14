import {Component, OnInit} from '@angular/core';
import {ClusterStatusService} from "../../../../services/cluster-status.service";
import {ConsumerService} from "../../../../services/consumer.service";
import {DatePipe, JsonPipe, NgForOf, NgIf} from "@angular/common";
import {IonicModule, IonModal, LoadingController} from "@ionic/angular";
import {RouterLink} from "@angular/router";
import {TranslateModule} from "@ngx-translate/core";
import {ComponentsModule} from "../../../../components/components.module";

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
    TranslateModule,
    DatePipe,
    ComponentsModule
  ]
})
export class ConsumersComponent implements OnInit {

  protected consumers: any[];
  protected consumer: any;
  protected consumerState: any;

  constructor(private service: ConsumerService,
              private loadingCtrl: LoadingController) {
  }

  ngOnInit() {
    this.service.findAllConsumers().then(c => this.consumers = c)
  }

  fetchConsumerState(c: any, modal: IonModal) {
    modal.dismiss();
    this.service.fetchConsumerState(c.consumerId).then(cs => {
      this.consumer = c;
      this.consumerState = cs;
      this.consumerState.deadEvents = this.consumerState.deadEvents.map(e => {
        e.ev = {
          aggregateId: e.aggregateId,
          context: e.context,
          createdAt: e.event.eventMessage.timestamp,
          deletedAt: e.deletedAt,
          eventName: e.eventName,
          eventSequenceNumber: e.eventSequenceNumber,
          metadata: e.event.eventMessage.metadata,
          event: e.event.eventMessage.serializedPayload.tree
        }
        return e;
      });
      modal.present();
    })
  }

  setEventRetry($event: any, de: any) {
    this.service.setRetryToDeadEvent(de.consumerId, de.eventSequenceNumber, $event.detail.checked);
  }

  async reprocessDeadEventQueue(consumer: any) {
    const l = await this.loadingCtrl.create();
    await l.present();
    try {
      await this.service.consumeDeadQueue(consumer.consumerId)
      const cs = await this.service.fetchConsumerState(consumer.consumerId)
      cs.deadEvents = cs.deadEvents.map(e => {
        e.ev = {
          aggregateId: e.aggregateId,
          context: e.context,
          createdAt: e.event.eventMessage.timestamp,
          deletedAt: e.deletedAt,
          eventName: e.eventName,
          eventSequenceNumber: e.eventSequenceNumber,
          metadata: e.event.eventMessage.metadata,
          event: e.event.eventMessage.serializedPayload.tree
        }
        return e;
      });
      this.consumerState = cs;
    }finally{
      await l.dismiss();
    }
  }
}
