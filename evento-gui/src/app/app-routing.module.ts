import {NgModule} from '@angular/core';
import {PreloadAllModules, RouterModule, Routes} from '@angular/router';

const routes: Routes = [
  {
    path: '',
    redirectTo: 'payload-catalog',
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
    path: 'application-flows/:handlerId',
    loadChildren: () => import('./pages/application-flows/application-flows.module').then(m => m.ApplicationFlowsPageModule)
  },
  {
    path: 'bundle-info/:identifier',
    loadChildren: () => import('./pages/bundle/bundle-info/bundle-info.module').then( m => m.BundleInfoPageModule)
  },
  {
    path: 'payload-catalog',
    loadChildren: () => import('./pages/catalog/payload-catalog/payload-catalog.module').then( m => m.PayloadCatalogPageModule)
  },
  {
    path: 'component-catalog',
    loadChildren: () => import('./pages/catalog/component-catalog/component-catalog.module').then( m => m.ComponentCatalogPageModule)
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
