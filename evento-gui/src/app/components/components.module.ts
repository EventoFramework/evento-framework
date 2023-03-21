import {NgModule} from '@angular/core';
import {WrappedContentComponent} from "./wrapped-content/wrapped-content.component";
import {InvokersHandlersDiagramComponent} from "./invokers-handlers-diagram/invokers-handlers-diagram.component";
import {AppModule} from "../app.module";
import {IonicModule} from "@ionic/angular";
import {ComponentHandlersDiagramComponent} from "./component-handlers-diagram/component-handlers-diagram.component";
import {BundleComponentsDiagramComponent} from "./bundle-components-diagram/bundle-components-diagram.component";
import {ApplicationGraphDiagramComponent} from "./application-graph-diagram/application-graph-diagram.component";


@NgModule({
  providers: [
    WrappedContentComponent,
    InvokersHandlersDiagramComponent,
    ApplicationGraphDiagramComponent,
    ComponentHandlersDiagramComponent,
    BundleComponentsDiagramComponent
  ],
    exports: [
        InvokersHandlersDiagramComponent,
        WrappedContentComponent,
        ComponentHandlersDiagramComponent,
        BundleComponentsDiagramComponent,
        ApplicationGraphDiagramComponent,
    ],
  imports: [
    IonicModule
  ],
  declarations: [
    WrappedContentComponent,
    InvokersHandlersDiagramComponent,
    ComponentHandlersDiagramComponent,
    BundleComponentsDiagramComponent,
    ApplicationGraphDiagramComponent]
})
export class ComponentsModule {
}
