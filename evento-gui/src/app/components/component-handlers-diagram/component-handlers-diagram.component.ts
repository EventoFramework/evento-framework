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
  selector: 'app-component-handlers-diagram',
  templateUrl: './component-handlers-diagram.component.html',
  styleUrls: ['./component-handlers-diagram.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager,
  standalone: false,
})
export class ComponentHandlersDiagramComponent implements AfterViewInit, OnDestroy {

  @Input() component;
  @ViewChild('container', {static: true}) container: ElementRef;

  private graph: EventoGraphHandle;

  constructor(private navController: NavController, private repository: RepositoryService) {
  }

  ngAfterViewInit() {
    const nodes: EventoNode[] = [];
    const edges: EventoEdge[] = [];

    this.component.handlers.forEach((h, idx) => {
      if (h.handlerType === 'EventSourcingHandler') {
        return;
      }
      const comp = `comp-${idx}`;
      nodes.push({
        id: comp,
        label: this.component.componentName,
        color: componentColor[this.component.componentType],
        primary: true,
        route: '/component-info/' + this.component.componentName,
        repo: {bundleId: h.bundleId, path: h.path, line: h.line},
      });

      const pay = `pay-${idx}`;
      nodes.push({
        id: pay,
        label: h.handledPayload.name,
        color: payloadColor[h.handledPayload.type],
        route: '/payload-info/' + h.handledPayload.name,
      });
      edges.push({source: pay, target: comp});

      if (h.returnType) {
        const ret = `ret-${idx}`;
        nodes.push({
          id: ret,
          label: h.returnType.name + (h.returnIsMultiple ? '[]' : ''),
          color: payloadColor[h.returnType.type],
          route: '/payload-info/' + h.returnType.name,
        });
        edges.push({source: comp, target: ret});
      }

      (Object.values(h.invocations) as any[]).forEach((i, j) => {
        const inv = `inv-${idx}-${j}`;
        nodes.push({
          id: inv,
          label: i.name,
          color: payloadColor[i.type],
          route: '/payload-info/' + i.name,
        });
        edges.push({source: comp, target: inv});
      });
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
