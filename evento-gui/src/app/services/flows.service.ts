import {Injectable} from '@angular/core';
import {environment} from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class FlowsService {

  constructor() { }

  getPerformanceModel() {
    return fetch(environment.eventoServerUrl + '/api/flows/').then(r => r.json());
  }

  getPerformanceModelFilter(filter, value) {
    return fetch(environment.eventoServerUrl + '/api/flows/'+filter+'/'+value).then(r => r.json());
  }
}
