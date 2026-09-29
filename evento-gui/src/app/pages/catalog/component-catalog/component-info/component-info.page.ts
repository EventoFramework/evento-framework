import {Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {CatalogService} from '../../../../services/catalog.service';
import {RepositoryService} from '../../../../services/repository.service';
import {stringToColour} from '../../../../services/utils';

@Component({
    selector: 'app-component-info',
    templateUrl: './component-info.page.html',
    styleUrls: ['./component-info.page.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class ComponentInfoPage implements OnInit {

  component;
  /** Set instead of {@link component} when a bare name matches components of several bundles. */
  ambiguous: {identifier: string, error: string, candidates: string[]};
  error: string;

  constructor(private route: ActivatedRoute,
              private catalogService: CatalogService,
              private repository: RepositoryService) {
  }

  async ngOnInit() {
    await this.repository.whenReady();
    // The page is reused when a candidate link points back at it, so follow the parameter.
    this.route.paramMap.subscribe(params => this.load(params.get('identifier')));
  }

  private load(identifier: string) {
    this.component = undefined;
    this.ambiguous = undefined;
    this.error = undefined;
    this.catalogService.findComponentByName(identifier).then(r => {
      if (r.ambiguous) {
        this.ambiguous = r;
        return;
      }
      r.bundleColor = stringToColour(r.bundleId);
      r.domains = new Set();
      r.repoLink = this.repository.link(r.bundleId, r.path, r.line);
      const invocations = {};
      for (const h of r.handlers) {
        r.domains.add(h.handledPayload.domain);
        h.repoLink = this.repository.link(h.bundleId, h.path, h.line);
        for(const i of Object.keys(h.invocations) as any[]){
          if(!invocations[h.invocations[i].name]){
            invocations[h.invocations[i].name] = {...h.invocations[i], lines: []}
          }
          invocations[h.invocations[i].name].lines.push(i);
        }
      }
      r.invocations = Object.values(invocations).map((i: any) => ({
        ...i,
        repoLinks: i.lines.map(l => ({line: l, link: this.repository.link(r.bundleId, r.path, l)}))
                          .filter(x => x.link),
      }));
      this.component = r;
    }).catch(e => this.error = e.message);
  }
}
