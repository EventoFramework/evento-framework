import {Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {ClusterStatusService} from "../../../../services/cluster-status.service";
import {ConsumerService} from "../../../../services/consumer.service";
import { DatePipe } from "@angular/common";
import {IonicModule, IonModal, LoadingController, ToastController} from "@ionic/angular";
import {TranslatePipe, TranslateDirective} from "@ngx-translate/core";
import {ComponentsModule} from "../../../../components/components.module";

@Component({
    selector: 'app-consumers',
    templateUrl: './consumers.component.html',
    styleUrls: ['./consumers.component.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [
    IonicModule,
    TranslatePipe, TranslateDirective,
    DatePipe,
    ComponentsModule
]
})
export class ConsumersComponent implements OnInit {

  protected consumers: any[];
  protected consumer: any;
  protected consumerState: any;
  // Row currently shown in the shared detail / error modals (one modal per table, not per row).
  protected selectedDead: any;

  constructor(private service: ConsumerService,
              private loadingCtrl: LoadingController,
              private toastCtrl: ToastController) {
  }

  /** Reports a refused or failed admin action (for instance a user without the ADMIN role). */
  private async reportFailure(e: any) {
    const toast = await this.toastCtrl.create({
      message: e?.message ?? String(e), color: 'danger', duration: 5000, position: 'top'
    });
    await toast.present();
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

  async setEventRetry($event: any, de: any) {
    const checked = $event.detail.checked;
    try {
      await this.service.setRetryToDeadEvent(de.consumerId, de.eventSequenceNumber, checked);
      de.retry = checked;
    } catch (e) {
      $event.target.checked = !checked;
      await this.reportFailure(e);
    }
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
    } catch (e) {
      await this.reportFailure(e);
    } finally {
      await l.dismiss();
    }
  }

  async deleteEvent(consumerId: any, eventSequenceNumber: any) {
    const l = await this.loadingCtrl.create();
    await l.present();
    try {
      await this.service.deleteDeadEvent(consumerId, eventSequenceNumber)
      const cs = await this.service.fetchConsumerState(consumerId)
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
    } catch (e) {
      await this.reportFailure(e);
    } finally {
      await l.dismiss();
    }
  }
}
