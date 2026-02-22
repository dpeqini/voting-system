import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import {AuthService} from '../core/services/auth.service';

@Component({
  selector: 'app-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex h-screen bg-slate-950 overflow-hidden">
      <!-- Sidebar -->
      <aside class="w-60 shrink-0 flex flex-col bg-slate-900 border-r border-slate-800">
        <!-- Brand -->
        <div class="px-5 py-5 border-b border-slate-800">
          <div class="flex items-center gap-3">
            <div class="w-8 h-8 rounded-lg bg-red-600 flex items-center justify-center shrink-0">
              <svg viewBox="0 0 24 24" class="w-4 h-4 fill-white" aria-hidden="true">
                <path d="M9 3H15L22 12L15 21H9L2 12L9 3Z"/>
              </svg>
            </div>
            <div>
              <p class="text-xs font-bold text-white tracking-wide leading-none">ALBANIA</p>
              <p class="text-xs text-slate-500 leading-none mt-0.5">E-Vote Admin</p>
            </div>
          </div>
        </div>

        <!-- Nav -->
        <nav class="flex-1 px-3 py-4 space-y-1 overflow-y-auto" aria-label="Main navigation">
          <a routerLink="/dashboard" routerLinkActive="bg-red-600/15 text-red-400 border-red-700/50"
             [routerLinkActiveOptions]="{exact: true}"
             class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 border border-transparent transition-all text-sm group">
            <svg class="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 5a1 1 0 011-1h4a1 1 0 011 1v5a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM14 5a1 1 0 011-1h4a1 1 0 011 1v2a1 1 0 01-1 1h-4a1 1 0 01-1-1V5zM4 15a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1H5a1 1 0 01-1-1v-4zM14 13a1 1 0 011-1h4a1 1 0 011 1v6a1 1 0 01-1 1h-4a1 1 0 01-1-1v-6z"/>
            </svg>
            <span>Dashboard</span>
          </a>

          <a routerLink="/elections" routerLinkActive="bg-red-600/15 text-red-400 border-red-700/50"
             class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 border border-transparent transition-all text-sm">
            <svg class="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4"/>
            </svg>
            <span>Elections</span>
          </a>

          <a routerLink="/verification" routerLinkActive="bg-red-600/15 text-red-400 border-red-700/50"
             class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 border border-transparent transition-all text-sm">
            <svg class="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"/>
            </svg>
            <span>Verification</span>
          </a>
        </nav>

        <!-- User footer -->
        <div class="px-3 py-3 border-t border-slate-800">
          <div class="flex items-center gap-3 px-2 py-2">
            <div class="w-8 h-8 rounded-full bg-slate-700 flex items-center justify-center text-xs font-bold text-white shrink-0"
                 aria-hidden="true">
              {{ initials() }}
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-xs font-medium text-white truncate">{{ authService.currentUser()?.fullName }}</p>
              <p class="text-xs text-slate-500 truncate">{{ authService.currentUser()?.role }}</p>
            </div>
          </div>
          <button
            (click)="logout()"
            class="mt-1 w-full flex items-center gap-2 px-3 py-1.5 rounded-lg text-slate-500 hover:text-red-400 hover:bg-red-950/20 transition-colors text-xs focus:outline-none focus:ring-2 focus:ring-red-600"
            aria-label="Sign out">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"/>
            </svg>
            Sign out
          </button>
        </div>
      </aside>

      <!-- Main content -->
      <main class="flex-1 overflow-y-auto bg-slate-950">
        <router-outlet />
      </main>
    </div>
  `,
})
export class LayoutComponent {
  authService = inject(AuthService);
  private router = inject(Router);

  initials() {
    const name = this.authService.currentUser()?.fullName ?? '';
    return name
      .split(' ')
      .map((n) => n[0])
      .join('')
      .toUpperCase()
      .slice(0, 2);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
