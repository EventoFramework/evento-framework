import {Injectable} from '@angular/core';
import {environment} from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ConsumerService {

  constructor() { }

  findAllConsumers() {
    return fetch(environment.eventoServerUrl + '/api/consumer/').then(r => r.json());
  }

  fetchConsumerState(consumerId: any) {
    return fetch(environment.eventoServerUrl + '/api/consumer/' + consumerId).then(r => r.json());

  }
}
