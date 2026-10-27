import {Injectable} from '@angular/core';
import {apiFetch, apiWrite} from './api';

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
    return apiWrite('/api/consumer/' + consumerId + '/event/' + eventSequenceNumber +'?retry=' + checked,
      {
      method: 'PUT'
    });
  }

  deleteDeadEvent(consumerId: any, eventSequenceNumber: any) {
    return apiWrite('/api/consumer/' + consumerId + '/event/' + eventSequenceNumber,
      {
      method: 'DELETE'
    });
  }

  async consumeDeadQueue(consumerId: any) {
    return apiWrite('/api/consumer/' + consumerId + '/consume-dead-queue',
      {
        method: 'POST'
      });

  }
}
