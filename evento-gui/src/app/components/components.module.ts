import {NgModule} from '@angular/core';
import {WrappedContentComponent} from "./wrapped-content/wrapped-content.component";
import {InvokersHandlersDiagramComponent} from "./invokers-handlers-diagram/invokers-handlers-diagram.component";
import {AppMapDiagramComponent} from "./app-map-diagram/app-map-diagram.component";
import {AppModule} from "../app.module";
import {IonicModule} from "@ionic/angular";
import {ComponentHandlersDiagramComponent} from "./component-handlers-diagram/component-handlers-diagram.component";
import {BundleComponentsDiagramComponent} from "./bundle-components-diagram/bundle-components-diagram.component";


@NgModule({
  providers: [
    WrappedContentComponent,
    InvokersHandlersDiagramComponent,
    AppMapDiagramComponent,
    ComponentHandlersDiagramComponent,
    BundleComponentsDiagramComponent
  ],
  exports: [
    InvokersHandlersDiagramComponent,
    WrappedContentComponent,
    ComponentHandlersDiagramComponent,
    BundleComponentsDiagramComponent,
  ],
  imports: [
    IonicModule
  ],
  declarations: [
    WrappedContentComponent,
    InvokersHandlersDiagramComponent,
    ComponentHandlersDiagramComponent,
    BundleComponentsDiagramComponent,
    AppMapDiagramComponent]
})
export class ComponentsModule {
}
