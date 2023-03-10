import {NgModule} from '@angular/core';
import {PreloadAllModules, RouterModule, Routes} from '@angular/router';

const routes: Routes = [
  {
    path: '',
    redirectTo: 'message-catalog',
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
    path: 'message-catalog',
    loadChildren: () => import('./pages/catalog/message-catalog/message-catalog.module').then( m => m.MessageCatalogPageModule)
  },
  {
    path: 'service-catalog',
    loadChildren: () => import('./pages/catalog/service-catalog/service-catalog.module').then( m => m.ServiceCatalogPageModule)
  },
  {
    path: 'domain-catalog',
    loadChildren: () => import('./pages/catalog/domain-catalog/domain-catalog.module').then( m => m.DomainCatalogPageModule)
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
