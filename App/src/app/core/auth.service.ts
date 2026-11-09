import { HttpClient } from '@angular/common/http';
import { Injectable, LOCALE_ID, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import { API_BASE } from './api-config';

/** The account, as the API describes it (`UserDto`). */
export interface User {
  id: string;
  email: string;
  emailVerified: boolean;
  displayName: string;
  locale: string;
  theme: string;
  siteRole: string;
}

interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

/** `unknown` until the first silent refresh has finished (the page was just opened). */
export type AuthStatus = 'unknown' | 'signed-in' | 'signed-out';

/**
 * Who is signed in. The access token lives only in memory (a page reload loses it, and the HTTP-only refresh cookie gets a new one).
 * The refresh token is never visible to the app's code.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly base = inject(API_BASE);
  private readonly language = inject(LOCALE_ID).slice(0, 2);

  private token: string | null = null;
  private restoring: Promise<void> | null = null;
  private refreshing: Observable<string> | null = null;

  private readonly statusSignal = signal<AuthStatus>('unknown');
  private readonly userSignal = signal<User | null>(null);

  readonly status = this.statusSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly signedIn = computed(() => this.statusSignal() === 'signed-in');

  /** The access token for the `Authorization` header, or null when signed out. */
  accessToken(): string | null {
    return this.token;
  }

  /** Tries once to continue the session from the refresh cookie; later calls wait for the same answer. */
  restore(): Promise<void> {
    this.restoring ??= firstValueFrom(
      this.refresh().pipe(
        map(() => undefined),
        catchError(() => of(undefined)),
      ),
    );
    return this.restoring;
  }

  /**
   * Asks for a new access token with the refresh cookie. Several calls at the same moment share one request, because the refresh token
   * rotates and a second use of the old one would close the whole login.
   */
  refresh(): Observable<string> {
    this.refreshing ??= this.http.post<TokenResponse>(`${this.base}/auth/refresh`, null).pipe(
      tap((r) => this.accept(r)),
      map((r) => r.accessToken),
      catchError((e) => {
        this.clear();
        return throwError(() => e);
      }),
      finalize(() => (this.refreshing = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.refreshing;
  }

  login(email: string, password: string): Observable<User> {
    return this.http.post<TokenResponse>(`${this.base}/auth/login`, { email, password }).pipe(
      tap((r) => this.accept(r)),
      map((r) => r.user),
    );
  }

  /** Creates the account and signs in at once (the API's register call gives no token). */
  register(email: string, password: string, displayName: string): Observable<User> {
    return this.http
      .post<User>(`${this.base}/auth/register`, { email, password, displayName, locale: this.language })
      .pipe(switchMap(() => this.login(email, password)));
  }

  /** Changes the display name, language or theme of the account (`PATCH /me`); what is left out stays as it is. */
  updateProfile(changes: Partial<Pick<User, 'displayName' | 'locale' | 'theme'>>): Observable<User> {
    return this.http.patch<User>(`${this.base}/me`, changes).pipe(tap((user) => this.userSignal.set(user)));
  }

  /** Which "sign in with" buttons the login screen shows (`['discord']`, or nothing when no provider is set up). */
  providers(): Observable<string[]> {
    return this.http.get<{ providers: string[] }>(`${this.base}/auth/oauth/providers`).pipe(
      map((r) => r.providers),
      catchError(() => of([] as string[])),
    );
  }

  /** Closes the login on the server and forgets it here. The app is signed out even when the server cannot be reached. */
  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/logout`, null).pipe(
      catchError(() => of(undefined)),
      tap(() => {
        this.clear();
        void this.router.navigate(['/login']);
      }),
      map(() => undefined),
    );
  }

  /** Called when the login can no longer be continued (the refresh failed): go to the login screen and come back after it. */
  expire(): void {
    this.clear();
    const url = this.router.url;
    if (!url.startsWith('/login') && !url.startsWith('/register')) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: url } });
    }
  }

  private accept(r: TokenResponse): void {
    this.token = r.accessToken;
    this.userSignal.set(r.user);
    this.statusSignal.set('signed-in');
  }

  private clear(): void {
    this.token = null;
    this.userSignal.set(null);
    this.statusSignal.set('signed-out');
  }
}
