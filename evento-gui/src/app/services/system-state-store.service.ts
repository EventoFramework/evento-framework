import { Injectable } from '@angular/core';
import {apiFetch} from './api';

@Injectable({
  providedIn: 'root'
})
export class SystemStateStoreService {

  constructor() { }

  async searchEvents(parameters = {}
  ) {
    return apiFetch('/api/system-state-store/event?' + new URLSearchParams(parameters as any).toString()).then(r => r.json());
  }

  async searchSnapshots(parameters = {}
  ) {
    return apiFetch('/api/system-state-store/snapshot?' + new URLSearchParams(parameters as any).toString()).then(r => r.json());
  }
}
