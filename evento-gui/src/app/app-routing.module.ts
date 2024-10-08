import {NgModule} from '@angular/core';
import {PreloadAllModules, RouterModule, Routes} from '@angular/router';

const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: 'bundle-list',
    loadChildren: () => import('./pages/bundle/bundle-list/bundle-list.module').then(m => m.BundleListPageModule)
  },
  {
    path: 'cluster-status',
    loadChildren: () => import('./pages/cluster-status/cluster-status.module').then(m => m.ClusterStatusPageModule)
  },
  {
    path: 'application-graph',
    loadChildren: () => import('./pages/application-graph/application-graph.module').then(m => m.ApplicationGraphPageModule)
  },
  {
    path: 'application-flows',
    loadChildren: () => import('./pages/application-flows/application-flows.module').then(m => m.ApplicationFlowsPageModule)
  },
  {
    path: 'bundle-info/:identifier',
    loadChildren: () => import('./pages/bundle/bundle-info/bundle-info.module').then(m => m.BundleInfoPageModule)
  },
  {
    path: 'payload-catalog',
    loadChildren: () => import('./pages/catalog/payload-catalog/payload-catalog.module').then(m => m.PayloadCatalogPageModule)
  },

  {
    path: 'payload-info/:identifier',
    loadChildren: () => import('./pages/catalog/payload-catalog/payload-info/payload-info.module').then(m => m.PayloadInfoPageModule)
  },
  {
    path: 'component-catalog',
    loadChildren: () => import('./pages/catalog/component-catalog/component-catalog.module').then(m => m.ComponentCatalogPageModule)
  },
  {
    path: 'component-info/:identifier',
    loadChildren: () => import('./pages/catalog/component-catalog/component-info/component-info.module')
      .then(m => m.ComponentInfoPageModule)
  },
  {
    path: 'dashboard',
    loadChildren: () => import('./pages/dashboard/dashboard.module').then( m => m.DashboardPageModule)
  },  {
    path: 'system-state-store',
    loadChildren: () => import('./pages/system-state-store/system-state-store.module').then( m => m.SystemStateStorePageModule)
  },



];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, {preloadingStrategy: PreloadAllModules})
  ],
  exports: [RouterModule]
})
export class AppRoutingModule {
}
