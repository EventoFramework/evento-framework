import {Component, Input, OnInit, ViewChild} from '@angular/core';
import {IonModal} from "@ionic/angular";

@Component({
  selector: 'app-event-detail-modal',
  templateUrl: './event-detail-modal.component.html',
  styleUrls: ['./event-detail-modal.component.scss'],
})
export class EventDetailModalComponent  implements OnInit {


  @Input() event: any
  @ViewChild('modal') modal: IonModal;

  constructor() { }

  ngOnInit() {}

  present() {
    this.modal.present();
  }
}
