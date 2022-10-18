import {Injectable} from '@angular/core';
import {environment} from "../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class RanchService {

  constructor() {
  }

  async findAll() {
    return fetch(environment.erServerUrl + '/api/ranch/').then(r => r.json());
  }

  async unregister(ranchName) {
    return fetch(environment.erServerUrl + '/api/ranch/' + ranchName, {
      method: 'DELETE'
    });
  }
}
