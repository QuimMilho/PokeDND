import { Routes } from '@angular/router';
import { publicOnlyGuard, signedInGuard } from './core/auth.guards';
import { Home } from './home';

/** The routes of chapter 11.2 that exist so far. Screens for cards load on demand. */
export const routes: Routes = [
  { path: '', component: Home, pathMatch: 'full', canActivate: [signedInGuard] },
  { path: 'login', canActivate: [publicOnlyGuard], loadComponent: () => import('./auth/login').then((m) => m.Login) },
  { path: 'register', canActivate: [publicOnlyGuard], loadComponent: () => import('./auth/register').then((m) => m.Register) },
  { path: 'cards', canActivate: [signedInGuard], loadComponent: () => import('./cards/card-browser').then((m) => m.CardBrowser) },
  // the fixed words come before :id
  { path: 'cards/export', canActivate: [signedInGuard], loadComponent: () => import('./cards/export').then((m) => m.CardExport) },
  { path: 'cards/jobs', canActivate: [signedInGuard], loadComponent: () => import('./cards/jobs').then((m) => m.ExportJobs) },
  { path: 'cards/:id', canActivate: [signedInGuard], loadComponent: () => import('./cards/card-detail').then((m) => m.CardDetail) },
  { path: '**', redirectTo: '' },
];
