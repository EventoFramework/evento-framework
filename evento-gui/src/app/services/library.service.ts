import { Injectable } from '@angular/core';
import {environment} from "../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class LibraryService {

  constructor() { }

  async findAll() {
    return fetch(environment.erServerUrl + '/api/library/').then(r => r.json());
  }
}
