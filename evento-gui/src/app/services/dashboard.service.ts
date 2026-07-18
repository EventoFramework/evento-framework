import {Injectable} from '@angular/core';
import {apiFetch} from './api';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {

  constructor() { }

  getDashboard() {
    return apiFetch('/api/dashboard').then(r => r.json());
  }
}
