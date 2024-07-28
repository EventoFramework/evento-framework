import {Injectable} from '@angular/core';
import {environment} from "../../environments/environment";

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
    return fetch(environment.eventoServerUrl + '/api/performance/component?' + new URLSearchParams({
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
    return fetch(environment.eventoServerUrl + '/api/performance/aggregate?' + new URLSearchParams({
      bundleId,
      componentId,
      from,
      to,
      interval,
      serviceTimeAggregationFunction
    } as any).toString()).then(r => r.json());
  }
}
