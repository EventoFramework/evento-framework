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

  getQueueNet(handlerId = null) {
    return fetch(environment.eventoServerUrl + '/api/handler/to-queue-net' + (handlerId ? ('/' + handlerId) : '')).then(r => r.json());
  }
}
