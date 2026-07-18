import {Injectable} from '@angular/core';
import {apiFetch} from './api';

@Injectable({
  providedIn: 'root'
})
export class FlowsService {

  constructor() { }

  getPerformanceModel() {
    return apiFetch('/api/flows/').then(r => r.json());
  }

  getPerformanceModelFilter(filter, value) {
    return apiFetch('/api/flows/'+filter+'/'+value).then(r => r.json());
  }
}
