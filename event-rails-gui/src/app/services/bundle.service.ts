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

  find(bundleName: string) {
    return fetch(environment.erServerUrl + '/api/bundle/' + bundleName).then(r => r.json());
  }

  putEnv(bundleName: string, key: any, value: any) {
    return fetch(environment.erServerUrl + '/api/bundle/' + bundleName + '/env/' + key, {
      method: 'POST',
      body: value
    })
  }

   removeEnv(bundleName: string, key: any) {
     return fetch(environment.erServerUrl + '/api/bundle/' + bundleName + '/env/' + key, {
       method: 'DELETE'
     })
  }

  putVmOption(bundleName: string, key: any, value: any) {
    return fetch(environment.erServerUrl + '/api/bundle/' + bundleName + '/vm-option/' + key, {
      method: 'POST',
      body:value
    })
  }

  removeVmOption(bundleName: string, key: any) {
    return fetch(environment.erServerUrl + '/api/bundle/' + bundleName + '/vm-option/' + key, {
      method: 'DELETE'
    })
  }
}
