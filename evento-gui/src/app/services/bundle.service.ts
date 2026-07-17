import {Injectable} from '@angular/core';
import {environment} from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class BundleService {

  constructor() {
  }

  async findAll() {
    return fetch(environment.eventoServerUrl + '/api/bundle/').then(r => r.json());
  }

  find(bundleId: string) {
    return fetch(environment.eventoServerUrl + '/api/bundle/' + bundleId).then(r => r.json());
  }
}
