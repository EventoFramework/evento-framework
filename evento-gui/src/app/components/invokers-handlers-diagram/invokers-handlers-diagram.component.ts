import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import {componentColor, payloadColor} from '../../services/utils';
import {NavController} from '@ionic/angular';
import {RepositoryService} from '../../services/repository.service';
import {createEventoGraph, EventoEdge, EventoGraphHandle, EventoNode} from '../graph/evento-graph';

@Component({
  selector: 'app-invokers-handlers-diagram',
  templateUrl: './invokers-handlers-diagram.component.html',
  styleUrls: ['./invokers-handlers-diagram.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager,
  standalone: false,
})
export class InvokersHandlersDiagramComponent implements AfterViewInit, OnDestroy {

  @Input() payload: any;
  @ViewChild('container', {static: true}) container: ElementRef;

  private graph: EventoGraphHandle;

  constructor(private navController: NavController, private repository: RepositoryService) {
  }

  ngAfterViewInit() {
    const nodes: EventoNode[] = [];
    const edges: EventoEdge[] = [];

    const pay = 'payload';
    nodes.push({
      id: pay,
      label: this.payload.name,
      color: payloadColor[this.payload.type],
      primary: true,
      route: '/payload-info/' + this.payload.name,
    });

    this.payload.returnedBy.forEach((r, idx) => {
      const id = `ret-${idx}`;
      nodes.push({
        id,
        label: r.name,
        color: componentColor[r.type],
        route: '/component-info/' + r.name,
        repo: {bundleId: r.bundleId, path: r.path, line: r.line},
      });
      edges.push({source: id, target: pay});
    });

    this.payload.invokers.forEach((i, idx) => {
      const id = `inv-${idx}`;
      nodes.push({
        id,
        label: i.name,
        color: componentColor[i.type],
        route: '/component-info/' + i.name,
      });
      edges.push({source: id, target: pay});
    });

    this.payload.subscribers.forEach((s, idx) => {
      const id = `sub-${idx}`;
      nodes.push({
        id,
        label: s.name,
        color: componentColor[s.type],
        route: '/component-info/' + s.name,
        repo: {bundleId: s.bundleId, path: s.path, line: s.line},
      });
      edges.push({source: pay, target: id});
    });

    this.graph = createEventoGraph(this.container.nativeElement, nodes, edges, {
      direction: 'RIGHT',
      onNavigate: (route) => this.navController.navigateForward(route),
      repoLink: (repo) => this.repository.link(repo.bundleId, repo.path, repo.line),
    });
  }

  ngOnDestroy() {
    this.graph?.destroy();
  }

}
