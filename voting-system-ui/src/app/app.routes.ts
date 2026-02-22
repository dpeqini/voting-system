import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./components/Login').then((m) => m.LoginComponent),
  },
  {
    path: '',
    loadComponent: () =>
      import('./components/Layout').then((m) => m.LayoutComponent),
    canActivate: [authGuard],
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full',
      },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./components/Dashboard').then((m) => m.DashboardComponent),
      },
      {
        path: 'elections',
        loadComponent: () =>
          import('./components/ElectionsList').then(
            (m) => m.ElectionsListComponent,
          ),
      },
      {
        path: 'elections/new',
        loadComponent: () =>
          import('./components/CreateElection').then(
            (m) => m.CreateElectionComponent,
          ),
      },
      {
        path: 'elections/:id',
        loadComponent: () =>
          import('./components/ElectionDetail').then(
            (m) => m.ElectionDetailComponent,
          ),
      },
      {
        path: 'verification',
        loadComponent: () =>
          import('./components/Verification').then((m) => m.VerificationComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
