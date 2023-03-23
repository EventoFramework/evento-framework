import { Injectable } from '@angular/core';
import {environment} from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class CatalogService {

  constructor() { }

  async findAllPayload() {
    return fetch(environment.eventoServerUrl + '/api/catalog/payload/').then(r => r.json());
  }

  findPayloadByName(identifier: string) {
    return fetch(environment.eventoServerUrl + '/api/catalog/payload/' + identifier).then(r => r.json());

  }

  async updatePayload(identifier, description, detail, domain) {
   return  fetch(environment.eventoServerUrl + '/api/catalog/payload/' + identifier, {
     method: 'PUT',
     body: JSON.stringify({
       detail,
       description,
       domain
     }),
     headers: {
       // eslint-disable-next-line @typescript-eslint/naming-convention
       'Content-Type': 'application/json'
     }
   });
  }

  async findAllComponent() {
    return fetch(environment.eventoServerUrl + '/api/catalog/component/').then(r => r.json());
  }

  findComponentByName(identifier: string) {
    return fetch(environment.eventoServerUrl + '/api/catalog/component/' + identifier).then(r => r.json());

  }

  async updateComponent(identifier, description, detail,) {
    return  fetch(environment.eventoServerUrl + '/api/catalog/component/' + identifier, {
      method: 'PUT',
      body: JSON.stringify({
        detail,
        description,
      }),
      headers: {
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'Content-Type': 'application/json'
      }
    });
  }
}
