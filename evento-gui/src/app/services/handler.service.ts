import { Injectable } from '@angular/core';
import {environment} from "../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class HandlerService {

  constructor() { }

  findAll(){
    return fetch(environment.erServerUrl + '/api/handler/').then(r => r.json());
  }

  getPetriNet(handlerId = null) {
    return fetch(environment.erServerUrl + '/api/handler/to-petri-net' + (handlerId ? ('/' + handlerId) : '')).then(r => r.json());
  }
}
