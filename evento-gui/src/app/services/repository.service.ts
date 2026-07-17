import {Injectable} from '@angular/core';
import {environment} from '../../environments/environment';

interface RepositoryInfo {
  repositoryUrl: string;
  linePrefix: string;
}

/**
 * Resolves "open on repository" source links.
 *
 * Repository browser base URL and line-anchor prefix are configured per bundle (server side) and
 * exposed on the bundle list endpoint. This service caches a {@code bundleId -> {repositoryUrl, linePrefix}}
 * map once and builds absolute links in the form {@code {repositoryUrl}/{path}#{linePrefix}{line}}.
 *
 * Link building is synchronous so it can be used from graph context-menu / window.open callbacks;
 * callers that render such links should await {@link whenReady} before showing them.
 */
@Injectable({
  providedIn: 'root'
})
export class RepositoryService {

  private readonly map = new Map<string, RepositoryInfo>();
  private readonly ready: Promise<void>;

  constructor() {
    this.ready = this.load();
  }

  private async load(): Promise<void> {
    try {
      const bundles = await fetch(environment.eventoServerUrl + '/api/bundle/').then(r => r.json());
      for (const b of bundles) {
        this.map.set(b.id, {repositoryUrl: b.repositoryUrl, linePrefix: b.linePrefix});
      }
    } catch (e) {
      // Leave the map empty; link() degrades to null (no link rendered) rather than a broken URL.
      console.warn('RepositoryService: unable to load bundle repository metadata', e);
    }
  }

  /** Resolves once the bundle repository metadata has been (attempted) loaded. */
  whenReady(): Promise<void> {
    return this.ready;
  }

  /**
   * Builds the absolute repository link for a source location, or {@code null} when no repository URL
   * is configured for the bundle (or the path is missing). Returning null lets templates hide the link
   * instead of producing a relative URL that resolves against the GUI origin.
   */
  link(bundleId: string, path: string, line?: number | string | null): string | null {
    if (!bundleId || !path) {
      return null;
    }
    const info = this.map.get(bundleId);
    if (!info || !info.repositoryUrl) {
      return null;
    }
    const base = info.repositoryUrl.replace(/\/+$/, '');
    const rel = String(path).replace(/^\/+/, '');
    let url = base + '/' + rel;
    if (line !== undefined && line !== null && String(line).length > 0) {
      url += '#' + (info.linePrefix || '') + line;
    }
    return url;
  }
}
