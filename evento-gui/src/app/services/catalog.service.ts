import {Injectable} from '@angular/core';
import {apiFetch} from './api';

@Injectable({
  providedIn: 'root'
})
export class CatalogService {

  constructor() { }

  async findAllPayload() {
    return apiFetch('/api/catalog/payload/').then(r => r.json());
  }

  findPayloadByName(identifier: string) {
    return apiFetch('/api/catalog/payload/' + identifier).then(r => r.json());

  }

  async findAllComponent() {
    return apiFetch('/api/catalog/component/').then(r => r.json());
  }

  findComponentByName(identifier: string) {
    return apiFetch('/api/catalog/component/' + identifier).then(r => r.json());

  }
}
