import {Injectable} from '@angular/core';
import {apiFetch} from './api';

@Injectable({
  providedIn: 'root'
})
export class ConsumerService {

  constructor() { }

  findAllConsumers() {
    return apiFetch('/api/consumer/').then(r => r.json());
  }

  fetchConsumerState(consumerId: any) {
    return apiFetch('/api/consumer/' + consumerId).then(r => r.json());

  }

  setRetryToDeadEvent(consumerId: any, eventSequenceNumber: any, checked) {
    return apiFetch('/api/consumer/' + consumerId + '/event/' + eventSequenceNumber +'?retry=' + checked,
      {
      method: 'PUT'
    }).then(r => r.json());
  }

  deleteDeadEvent(consumerId: any, eventSequenceNumber: any) {
    return apiFetch('/api/consumer/' + consumerId + '/event/' + eventSequenceNumber,
      {
      method: 'DELETE'
    }).then(r => r.json());
  }

  async consumeDeadQueue(consumerId: any) {
    return apiFetch('/api/consumer/' + consumerId + '/consume-dead-queue',
      {
        method: 'POST'
      }).then(r => r.json());

  }
}
