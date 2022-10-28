import {Injectable} from '@angular/core';
import {environment} from "../../environments/environment";
import {Observable} from "rxjs";

@Injectable({
  providedIn: 'root'
})
export class BundleService {

  constructor() {
  }

  async findAll() {
    return fetch(environment.erServerUrl + '/api/bundle/').then(r => r.json());
  }

  async unregister(bundleName) {
    return fetch(environment.erServerUrl + '/api/bundle/' + bundleName, {
      method: 'DELETE'
    });
  }

  // Returns an observable
  register(bundle) {

    // Create form data
    const formData = new FormData();

    // Store form name as "file" with file data
    formData.append("bundle", bundle, bundle.name);

    // Make http post request over api
    // with formData as req
    return fetch(environment.erServerUrl + '/api/bundle/', {
      method: 'POST',
      body: formData
    })
  }
}
