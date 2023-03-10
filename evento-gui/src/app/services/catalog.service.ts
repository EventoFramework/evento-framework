import { Injectable } from '@angular/core';
import {environment} from "../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class CatalogService {

  constructor() { }

  async findAll() {
    return fetch(environment.eventoServerUrl + '/api/catalog/').then(r => r.json());
  }

  findByName(identifier: string) {
    return fetch(environment.eventoServerUrl + '/api/catalog/' + identifier).then(r => r.json());

  }
}
