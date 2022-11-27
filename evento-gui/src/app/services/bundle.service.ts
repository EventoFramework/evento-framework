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

  async unregister(bundleId) {
    return fetch(environment.erServerUrl + '/api/bundle/' + bundleId, {
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

  find(bundleId: string) {
    return fetch(environment.erServerUrl + '/api/bundle/' + bundleId).then(r => r.json());
  }

  putEnv(bundleId: string, key: any, value: any) {
    return fetch(environment.erServerUrl + '/api/bundle/' + bundleId + '/env/' + key, {
      method: 'POST',
      body: value
    })
  }

   removeEnv(bundleId: string, key: any) {
     return fetch(environment.erServerUrl + '/api/bundle/' + bundleId + '/env/' + key, {
       method: 'DELETE'
     })
  }

  putVmOption(bundleId: string, key: any, value: any) {
    return fetch(environment.erServerUrl + '/api/bundle/' + bundleId + '/vm-option/' + key, {
      method: 'POST',
      body:value
    })
  }

  removeVmOption(bundleId: string, key: any) {
    return fetch(environment.erServerUrl + '/api/bundle/' + bundleId + '/vm-option/' + key, {
      method: 'DELETE'
    })
  }
}
