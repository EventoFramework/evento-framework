import {NgModule} from '@angular/core';
import {WrappedContentComponent} from "./wrapped-content/wrapped-content.component";
import {InvokersHandlersDiagramComponent} from "./invokers-handlers-diagram/invokers-handlers-diagram.component";
import {AppMapDiagramComponent} from "./app-map-diagram/app-map-diagram.component";
import {AppModule} from "../app.module";
import {IonicModule} from "@ionic/angular";


@NgModule({
    providers: [
        WrappedContentComponent,
        InvokersHandlersDiagramComponent,
        AppMapDiagramComponent
    ],
    exports: [
        InvokersHandlersDiagramComponent,
        WrappedContentComponent
    ],
    imports: [
        IonicModule
    ],
    declarations: [
        WrappedContentComponent,
        InvokersHandlersDiagramComponent,
        AppMapDiagramComponent]
})
export class ComponentsModule {
}
