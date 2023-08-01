import {Injectable} from '@angular/core';
import {environment} from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {

  constructor() { }

  getDashboard() {
    return fetch(environment.eventoServerUrl + '/api/dashboard/').then(r => r.json());
  }
}
