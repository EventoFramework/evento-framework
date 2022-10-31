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
}
