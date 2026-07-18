import {Injectable, NgZone} from '@angular/core';
import {Observable} from 'rxjs';
import {apiFetch} from './api';

@Injectable({
  providedIn: 'root'
})
export class ClusterStatusService {

  constructor(private zone: NgZone) {
  }

  /**
   * Live cluster view over Server-Sent Events.
   *
   * Consumed with fetch + ReadableStream instead of EventSource: EventSource cannot send
   * an Authorization header, and streaming the response ourselves keeps the Basic
   * credential out of the URL (the old scheme passed the token as a query parameter,
   * which lands in access logs). Only the `data:` field of the SSE framing is used —
   * that is all the server emits.
   */
  getView(): Observable<any> {

    return new Observable<any>(
      observer => {
        const abort = new AbortController();

        apiFetch('/api/cluster-status/view', {
          signal: abort.signal,
          headers: {Accept: 'text/event-stream'}
        }).then(async res => {
          if (!res.ok || !res.body) {
            throw new Error('SSE connection failed: ' + res.status);
          }
          const reader = res.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';
          for (; ;) {
            const {done, value} = await reader.read();
            if (done) {
              break;
            }
            buffer += decoder.decode(value, {stream: true});
            // SSE events are separated by a blank line.
            let sep: number;
            while ((sep = buffer.indexOf('\n\n')) >= 0) {
              const rawEvent = buffer.slice(0, sep);
              buffer = buffer.slice(sep + 2);
              const data = rawEvent.split('\n')
                .filter(line => line.startsWith('data:'))
                .map(line => line.slice(5).trimStart())
                .join('\n');
              if (data.length) {
                this.zone.run(() => observer.next(JSON.parse(data)));
              }
            }
          }
          this.zone.run(() => observer.complete());
        }).catch(err => {
          if (!abort.signal.aborted) {
            this.zone.run(() => observer.error(err));
          }
        });

        observer.add(() => abort.abort());
      }
    );
  }

  getAttendedView() {
    return apiFetch('/api/cluster-status/attended-view').then(r => r.json());
  }
}
