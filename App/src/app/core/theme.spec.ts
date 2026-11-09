import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { ThemeService, isTheme } from './theme';

const user = (theme: string) => ({ id: '1', email: 'a@example.test', emailVerified: true, displayName: 'Anna', locale: 'en', theme, siteRole: 'none' });
const tokens = (theme: string) => ({ accessToken: 'T', tokenType: 'Bearer', expiresIn: 900, user: user(theme) });
const html = () => document.documentElement;

describe('Theme switch', () => {
  beforeEach(() => {
    localStorage.removeItem('pokednd.theme');
    html().removeAttribute('data-theme');
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    localStorage.removeItem('pokednd.theme');
    html().removeAttribute('data-theme');
  });

  it('starts as "system" and writes it on the page', () => {
    const theme = TestBed.inject(ThemeService);
    expect(theme.theme()).toBe('system');
    expect(html().getAttribute('data-theme')).toBe('system');
  });

  it('switches between system, light and dark, and keeps the choice in the browser', () => {
    const theme = TestBed.inject(ThemeService);
    for (const t of ['dark', 'light', 'system'] as const) {
      theme.choose(t);
      expect(theme.theme()).toBe(t);
      expect(html().getAttribute('data-theme')).toBe(t);
      expect(localStorage.getItem('pokednd.theme')).toBe(t);
    }
  });

  it('does not call the API when nobody is signed in', () => {
    TestBed.inject(ThemeService).choose('dark'); // verify() in afterEach fails on any request
    expect(html().getAttribute('data-theme')).toBe('dark');
  });

  it('saves the choice on the account when signed in', () => {
    const mock = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthService);
    const theme = TestBed.inject(ThemeService);
    auth.login('a@example.test', 'pw').subscribe();
    mock.expectOne('/api/v1/auth/login').flush(tokens('system'));
    TestBed.tick();

    theme.choose('dark');
    const patch = mock.expectOne('/api/v1/me');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ theme: 'dark' });
    patch.flush(user('dark'));
    expect(auth.user()?.theme).toBe('dark');
    expect(theme.theme()).toBe('dark');
  });

  it('uses the theme saved on the account when someone signs in', () => {
    const mock = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthService);
    const theme = TestBed.inject(ThemeService);
    auth.login('a@example.test', 'pw').subscribe();
    mock.expectOne('/api/v1/auth/login').flush(tokens('dark'));
    TestBed.tick();
    expect(theme.theme()).toBe('dark');
    expect(html().getAttribute('data-theme')).toBe('dark');
  });

  it('ignores a stored value that is not a theme', () => {
    localStorage.setItem('pokednd.theme', 'neon');
    expect(TestBed.inject(ThemeService).theme()).toBe('system');
    expect(isTheme('neon')).toBeFalse();
    expect(isTheme('dark')).toBeTrue();
  });
});
