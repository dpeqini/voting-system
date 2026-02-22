import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import {AuthService} from '../core/services/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-screen bg-slate-950 flex items-center justify-center p-4">
      <!-- Background grid -->
      <div class="absolute inset-0 opacity-5"
           style="background-image: linear-gradient(rgba(239,68,68,0.3) 1px, transparent 1px), linear-gradient(90deg, rgba(239,68,68,0.3) 1px, transparent 1px); background-size: 40px 40px;">
      </div>

      <div class="relative w-full max-w-md">
        <!-- Header -->
        <div class="text-center mb-8">
          <!-- Albanian Eagle Emblem -->
          <div class="inline-flex items-center justify-center w-20 h-20 rounded-full bg-red-600 mb-5 shadow-lg shadow-red-900/50">
            <svg viewBox="0 0 100 100" class="w-12 h-12 fill-white" aria-hidden="true">
              <path d="M50 8 C40 8 32 14 28 22 C24 18 18 16 14 20 C10 24 12 32 18 36 C14 38 10 44 14 50 C18 56 26 56 32 52 C34 60 42 66 50 66 C58 66 66 60 68 52 C74 56 82 56 86 50 C90 44 86 38 82 36 C88 32 90 24 86 20 C82 16 76 18 72 22 C68 14 60 8 50 8 Z M50 18 C56 18 62 22 64 28 L36 28 C38 22 44 18 50 18 Z M30 38 C26 36 22 30 26 26 C28 24 32 26 34 30 C32 32 30 35 30 38 Z M70 38 C70 35 68 32 66 30 C68 26 72 24 74 26 C78 30 74 36 70 38 Z M50 56 C44 56 38 52 36 46 L64 46 C62 52 56 56 50 56 Z"/>
              <path d="M46 68 L46 92 L40 92 L40 96 L60 96 L60 92 L54 92 L54 68 Z"/>
              <path d="M35 78 L25 78 L25 84 L35 84 Z M65 78 L75 78 L75 84 L65 84 Z"/>
            </svg>
          </div>
          <h1 class="text-2xl font-bold text-white tracking-tight" style="font-family: 'Georgia', serif;">
            Republika e Shqipërisë
          </h1>
          <p class="text-slate-400 text-sm mt-1 tracking-widest uppercase">Election Administration Portal</p>
        </div>

        <!-- Card -->
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-8 shadow-2xl">
          <h2 class="text-lg font-semibold text-white mb-6">Administrator Sign In</h2>

          @if (errorMsg()) {
            <div class="mb-5 p-3 rounded-lg bg-red-950/60 border border-red-800/50 text-red-300 text-sm flex items-start gap-2"
                 role="alert" aria-live="assertive">
              <svg class="w-4 h-4 mt-0.5 shrink-0 text-red-400" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true">
                <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
              </svg>
              {{ errorMsg() }}
            </div>
          }

          <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
            <div class="space-y-4">
              <div>
                <label for="email" class="block text-sm font-medium text-slate-300 mb-1.5">
                  Email Address
                </label>
                <input
                  id="email"
                  type="email"
                  formControlName="email"
                  autocomplete="email"
                  class="w-full px-4 py-2.5 bg-slate-800 border rounded-lg text-white placeholder-slate-500 text-sm transition-colors focus:outline-none focus:ring-2 focus:ring-red-600 focus:border-transparent"
                  [class]="emailInvalid() ? 'border-red-700' : 'border-slate-700'"
                  placeholder="admin@voting.albania.gov"
                  [attr.aria-describedby]="emailInvalid() ? 'email-error' : null"
                  [attr.aria-invalid]="emailInvalid()"
                />
                @if (emailInvalid()) {
                  <p id="email-error" class="mt-1 text-xs text-red-400">Enter a valid email address</p>
                }
              </div>

              <div>
                <label for="password" class="block text-sm font-medium text-slate-300 mb-1.5">
                  Password
                </label>
                <input
                  id="password"
                  type="password"
                  formControlName="password"
                  autocomplete="current-password"
                  class="w-full px-4 py-2.5 bg-slate-800 border rounded-lg text-white placeholder-slate-500 text-sm transition-colors focus:outline-none focus:ring-2 focus:ring-red-600 focus:border-transparent"
                  [class]="passwordInvalid() ? 'border-red-700' : 'border-slate-700'"
                  placeholder="••••••••"
                  [attr.aria-describedby]="passwordInvalid() ? 'password-error' : null"
                  [attr.aria-invalid]="passwordInvalid()"
                />
                @if (passwordInvalid()) {
                  <p id="password-error" class="mt-1 text-xs text-red-400">Password is required</p>
                }
              </div>
            </div>

            <button
              type="submit"
              [disabled]="loading()"
              class="mt-6 w-full flex items-center justify-center gap-2 px-4 py-3 bg-red-600 hover:bg-red-700 disabled:opacity-60 disabled:cursor-not-allowed rounded-lg text-white text-sm font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2 focus:ring-offset-slate-900"
              aria-label="Sign in to administrator portal"
            >
              @if (loading()) {
                <svg class="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24" aria-hidden="true">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                </svg>
                <span>Authenticating…</span>
              } @else {
                <span>Sign In</span>
              }
            </button>
          </form>
        </div>

        <p class="text-center text-xs text-slate-600 mt-6">
          Komisioni Qendror i Zgjedhjeve · Secure Portal v2.0
        </p>
      </div>
    </div>
  `,
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  loading = signal(false);
  errorMsg = signal('');

  emailInvalid() {
    const ctrl = this.form.controls.email;
    return ctrl.invalid && ctrl.touched;
  }

  passwordInvalid() {
    const ctrl = this.form.controls.password;
    return ctrl.invalid && ctrl.touched;
  }

  onSubmit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.loading()) return;

    this.loading.set(true);
    this.errorMsg.set('');

    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.router.navigate(['/dashboard']);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMsg.set(
          err.error?.message || err.error?.error || 'Invalid email or password',
        );
      },
    });
  }
}
