import { Routes } from '@angular/router';
import { publicOnlyGuard, signedInGuard } from './core/auth.guards';
import { Home } from './home';

/** The routes of chapter 11.2 that exist so far. Screens load on demand. */
export const routes: Routes = [
  { path: '', component: Home, pathMatch: 'full', canActivate: [signedInGuard] },
  { path: 'login', canActivate: [publicOnlyGuard], loadComponent: () => import('./auth/login').then((m) => m.Login) },
  { path: 'register', canActivate: [publicOnlyGuard], loadComponent: () => import('./auth/register').then((m) => m.Register) },
  { path: 'account', canActivate: [signedInGuard], loadComponent: () => import('./account/account').then((m) => m.Account) },
  { path: 'games', canActivate: [signedInGuard], loadComponent: () => import('./games/games').then((m) => m.Games) },
  // the fixed word comes before :id
  { path: 'games/new', canActivate: [signedInGuard], loadComponent: () => import('./games/new-game').then((m) => m.NewGame) },
  { path: 'games/:id', canActivate: [signedInGuard], loadComponent: () => import('./games/game-home').then((m) => m.GameHome) },
  { path: 'games/:id/lobby', canActivate: [signedInGuard], loadComponent: () => import('./games/lobby').then((m) => m.Lobby) },
  { path: 'games/:id/trainer', canActivate: [signedInGuard], loadComponent: () => import('./trainers/trainer-page').then((m) => m.TrainerPage) },
  {
    path: 'games/:id/trainer/create',
    canActivate: [signedInGuard],
    data: { edit: false },
    loadComponent: () => import('./trainers/trainer-wizard').then((m) => m.TrainerWizard),
  },
  {
    path: 'games/:id/trainer/edit',
    canActivate: [signedInGuard],
    data: { edit: true },
    loadComponent: () => import('./trainers/trainer-wizard').then((m) => m.TrainerWizard),
  },
  {
    path: 'games/:id/admin/settings',
    canActivate: [signedInGuard],
    loadComponent: () => import('./settings/settings-screen').then((m) => m.SettingsScreen),
  },
  { path: 'cards', canActivate: [signedInGuard], loadComponent: () => import('./cards/card-browser').then((m) => m.CardBrowser) },
  // the fixed words come before :id
  { path: 'cards/export', canActivate: [signedInGuard], loadComponent: () => import('./cards/export').then((m) => m.CardExport) },
  { path: 'cards/jobs', canActivate: [signedInGuard], loadComponent: () => import('./cards/jobs').then((m) => m.ExportJobs) },
  { path: 'cards/:id', canActivate: [signedInGuard], loadComponent: () => import('./cards/card-detail').then((m) => m.CardDetail) },
  { path: '**', redirectTo: '' },
];
