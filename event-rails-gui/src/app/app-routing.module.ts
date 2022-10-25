import {NgModule} from '@angular/core';
import {PreloadAllModules, RouterModule, Routes} from '@angular/router';

const routes: Routes = [
  {
    path: '',
    redirectTo: 'folder/Inbox',
    pathMatch: 'full'
  },
  {
    path: 'ranch-list',
    loadChildren: () => import('./pages/ranch/ranch-list/ranch-list.module').then(m => m.RanchListPageModule)
  },
  {
    path: 'library-page',
    loadChildren: () => import('./pages/library/library-page/library-page.module').then( m => m.LibraryPagePageModule)
  },
  {
    path: 'cluster-status',
    loadChildren: () => import('./pages/cluster-status/cluster-status.module').then( m => m.ClusterStatusPageModule)
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
