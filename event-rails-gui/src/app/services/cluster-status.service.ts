import {Injectable, NgZone} from '@angular/core';
import {Observable} from "rxjs";
import {environment} from "../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class ClusterStatusService {

  constructor(private zone: NgZone) { }

  getView(): Observable<any> {

    return new Observable<any>(
      observer => {

        let source = new EventSource(environment.erServerUrl + '/api/cluster-status/view');
        source.onmessage = event => {
          this.zone.run(() => {
            observer.next(event.data)
          })
        }

        source.onerror = event => {
          this.zone.run(() => {
            observer.error(event)
          })
        }
      }
    )
  }

  getAvailableView(): Observable<any> {

    return new Observable<any>(
      observer => {

        let source = new EventSource(environment.erServerUrl + '/api/cluster-status/available-view');
        source.onmessage = event => {
          this.zone.run(() => {
            observer.next(JSON.parse(event.data))
          })
        }

        source.onerror = event => {
          this.zone.run(() => {
            observer.error(event)
          })
        }
      }
    )
  }
}
