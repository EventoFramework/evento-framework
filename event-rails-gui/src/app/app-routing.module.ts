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
    path: 'ranch-upload',
    loadChildren: () => import('./pages/ranch/ranch-upload/ranch-upload.module').then(m => m.RanchUploadPageModule)
  },
  {
    path: 'library-page',
    loadChildren: () => import('./pages/library/library-page/library-page.module').then( m => m.LibraryPagePageModule)
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
