import { Injectable } from '@angular/core';
import {environment} from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class FlowsService {

  constructor() { }

  getQueueNet() {
    return fetch(environment.eventoServerUrl + '/api/flows/').then(r => r.json());
  }

  getQueueNetFilter(filter, value) {
    return fetch(environment.eventoServerUrl + '/api/flows/'+filter+'/'+value).then(r => r.json());
  }
}
