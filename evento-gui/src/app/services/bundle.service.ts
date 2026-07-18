import {Injectable} from '@angular/core';
import {apiFetch} from './api';

@Injectable({
  providedIn: 'root'
})
export class BundleService {

  constructor() {
  }

  async findAll() {
    return apiFetch('/api/bundle/').then(r => r.json());
  }

  find(bundleId: string) {
    return apiFetch('/api/bundle/' + bundleId).then(r => r.json());
  }
}
