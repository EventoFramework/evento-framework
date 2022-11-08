import {NgModule} from '@angular/core';
import {PreloadAllModules, RouterModule, Routes} from '@angular/router';

const routes: Routes = [
  {
    path: '',
    redirectTo: 'application-map',
    pathMatch: 'full'
  },
  {
    path: 'bundle-list',
    loadChildren: () => import('./pages/bundle/bundle-list/bundle-list.module').then(m => m.BundleListPageModule)
  },
  {
    path: 'library-page',
    loadChildren: () => import('./pages/library/library-page/library-page.module').then( m => m.LibraryPagePageModule)
  },
  {
    path: 'cluster-status',
    loadChildren: () => import('./pages/cluster-status/cluster-status.module').then( m => m.ClusterStatusPageModule)
  },
  {
    path: 'application-map',
    loadChildren: () => import('./pages/application-map/application-map.module').then( m => m.ApplicationMapPageModule)
  },
  {
    path: 'application-petri-net',
    loadChildren: () => import('./pages/application-petri-net/application-petri-net.module').then( m => m.ApplicationPetriNetPageModule)
  }


];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, {preloadingStrategy: PreloadAllModules})
  ],
  exports: [RouterModule]
})
export class AppRoutingModule {
}
