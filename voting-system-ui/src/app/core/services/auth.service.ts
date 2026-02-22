import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Observable, tap, catchError, throwError } from 'rxjs';
import {AdminAuthResponse, AuthRequest} from '../interfaces';

const API = 'http://localhost:8081/api/v1';
const TOKEN_KEY = 'admin_access_token';
const REFRESH_KEY = 'admin_refresh_token';
const USER_KEY = 'admin_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private platformId = inject(PLATFORM_ID);
  private isBrowser = isPlatformBrowser(this.platformId);

  currentUser = signal<AdminAuthResponse | null>(this.loadUser());
  isAuthenticated = computed(() => !!this.currentUser());
  isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  private loadUser(): AdminAuthResponse | null {
    if (!this.isBrowser) return null;
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }

  login(request: AuthRequest): Observable<AdminAuthResponse> {
    return this.http.post<AdminAuthResponse>(`${API}/admin/auth/login`, request).pipe(
      tap((res) => {
        if (this.isBrowser) {
          localStorage.setItem(TOKEN_KEY, res.accessToken);
          localStorage.setItem(REFRESH_KEY, res.refreshToken);
          localStorage.setItem(USER_KEY, JSON.stringify(res));
        }
        this.currentUser.set(res);
      }),
    );
  }

  refreshToken(): Observable<AdminAuthResponse> {
    const token = this.getRefreshToken();
    return this.http
      .post<AdminAuthResponse>(
        `${API}/admin/auth/refresh`,
        {},
        { headers: { Authorization: `Bearer ${token}` } },
      )
      .pipe(
        tap((res) => {
          if (this.isBrowser) {
            localStorage.setItem(TOKEN_KEY, res.accessToken);
            localStorage.setItem(REFRESH_KEY, res.refreshToken);
            localStorage.setItem(USER_KEY, JSON.stringify(res));
          }
          this.currentUser.set(res);
        }),
        catchError((err) => {
          this.logout();
          return throwError(() => err);
        }),
      );
  }

  logout(): void {
    if (this.isBrowser) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(REFRESH_KEY);
      localStorage.removeItem(USER_KEY);
    }
    this.currentUser.set(null);
  }

  getAccessToken(): string | null {
    return this.isBrowser ? localStorage.getItem(TOKEN_KEY) : null;
  }

  getRefreshToken(): string | null {
    return this.isBrowser ? localStorage.getItem(REFRESH_KEY) : null;
  }
}
