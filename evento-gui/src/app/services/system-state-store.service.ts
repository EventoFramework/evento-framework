import { Injectable } from '@angular/core';
import {environment} from "../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class SystemStateStoreService {

  constructor() { }

  async searchEvents(parameters = {}
  ) {
    return fetch(environment.eventoServerUrl + '/api/system-state-store/event?' + new URLSearchParams(parameters as any).toString()).then(r => r.json());
  }
}
