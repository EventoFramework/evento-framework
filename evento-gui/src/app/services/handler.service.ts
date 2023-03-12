import { Injectable } from '@angular/core';
import {environment} from "../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class HandlerService {

  constructor() { }

  findAll(){
    return fetch(environment.eventoServerUrl + '/api/handler/').then(r => r.json());
  }

  getQueueNet() {
    return fetch(environment.eventoServerUrl + '/api/handler/to-queue-net').then(r => r.json());
  }

  getQueueNetFilter(filter, value) {
    return fetch(environment.eventoServerUrl + '/api/handler/to-queue-net/'+filter+'/'+value).then(r => r.json());
  }
}
