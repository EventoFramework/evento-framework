import {Injectable} from '@angular/core';
import {apiFetch} from './api';

@Injectable({
  providedIn: 'root'
})
export class PerformanceService {

  constructor() {
  }

  async getComponentPerformanceTs(
    bundleId: string,
    componentId: string,
    from: string,
    to: string,
    interval: number,
    serviceTimeAggregationFunction: string
  ) {
    return apiFetch('/api/performance/component?' + new URLSearchParams({
      bundleId,
      componentId,
      from,
      to,
      interval,
      serviceTimeAggregationFunction
    } as any).toString()).then(r => r.json());
  }

  async getAggregatePerformanceTs(
    bundleId: string,
    componentId: string,
    from: string,
    to: string,
    interval: number,
    serviceTimeAggregationFunction: string
  ) {
    return apiFetch('/api/performance/aggregate?' + new URLSearchParams({
      bundleId,
      componentId,
      from,
      to,
      interval,
      serviceTimeAggregationFunction
    } as any).toString()).then(r => r.json());
  }
}
