import {Injectable} from '@angular/core';
import {apiFetch} from './api';

@Injectable({
  providedIn: 'root'
})
export class CatalogService {

  constructor() { }

  async findAllPayload() {
    return apiFetch('/api/catalog/payload/').then(r => r.json());
  }

  findPayloadByName(identifier: string) {
    return apiFetch('/api/catalog/payload/' + identifier).then(r => r.json());

  }

  async findAllComponent() {
    return apiFetch('/api/catalog/component/').then(r => r.json());
  }

  /**
   * A component by id (`bundleId:componentName`) or bare name. A bare name that several bundles
   * use answers 409 with the candidate ids: that resolves to `{ambiguous: true, candidates}`
   * instead of a component, so callers never read an error body as a component.
   */
  findComponentByName(identifier: string): Promise<any> {
    return apiFetch('/api/catalog/component/' + encodeURIComponent(identifier)).then(async r => {
      const body = await r.json().catch(() => ({}));
      if (r.status === 409) {
        return {ambiguous: true, identifier, error: body.error, candidates: body.candidates || []};
      }
      if (!r.ok) {
        throw new Error(body.error || body.message || ('HTTP ' + r.status));
      }
      return body;
    });
  }
}
