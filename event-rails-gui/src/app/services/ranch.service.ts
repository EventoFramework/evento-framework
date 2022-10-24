import {Injectable} from '@angular/core';
import {environment} from "../../environments/environment";
import {Observable} from "rxjs";

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

  // Returns an observable
  register(ranch) {

    // Create form data
    const formData = new FormData();

    // Store form name as "file" with file data
    formData.append("ranch", ranch, ranch.name);

    // Make http post request over api
    // with formData as req
    return fetch(environment.erServerUrl + '/api/ranch/', {
      method: 'POST',
      body: formData
    })
  }
}
