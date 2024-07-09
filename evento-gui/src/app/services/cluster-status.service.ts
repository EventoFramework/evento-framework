import {Injectable, NgZone} from '@angular/core';
import {Observable} from 'rxjs';
import {environment} from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ClusterStatusService {

  constructor(private zone: NgZone) {
  }

  getView(): Observable<any> {

    return new Observable<any>(
      observer => {

        const source = new EventSource(environment.eventoServerUrl +
          '/api/cluster-status/view?token=' + localStorage.getItem('evento_server_web_token'));
        source.onmessage = event => {
          this.zone.run(() => {
            observer.next(JSON.parse(event.data));
          });
        };

        source.onerror = event => {
          this.zone.run(() => {
            observer.error(event);
          });
        };

        observer.add(() => {
          source.close();
        });


      }
    );
  }

  getAttendedView() {
    return fetch(environment.eventoServerUrl + '/api/cluster-status/attended-view').then(r => r.json());
  }

  async spawn(bundleId: any) {
    return fetch(environment.eventoServerUrl + '/api/cluster-status/spawn/' + bundleId, {
      method: 'POST'
    });
  }

  async kill(bundleId, instanceId) {
    return fetch(environment.eventoServerUrl + '/api/cluster-status/kill/' + bundleId + '/' + instanceId, {
      method: 'DELETE'
    });
  }
}
