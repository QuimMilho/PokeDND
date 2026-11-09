import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { NAVIGATE } from '../core/browser';
import { flushUi, press } from '../../testing/ui';
import { Account } from './account';

@Component({ selector: 'app-blank-account', template: '' })
class Blank {}

const USER = { id: '1', email: 'anna@example.test', emailVerified: true, displayName: 'Anna', locale: 'en', theme: 'system', siteRole: 'none' };
const SESSIONS = [
  { id: 's1', userAgent: 'Firefox on Linux', createdAt: '2026-11-01T10:00:00Z', lastUsedAt: null, expiresAt: '2026-12-01T10:00:00Z', current: true },
  { id: 's2', userAgent: 'Chrome on Android', createdAt: '2026-11-02T10:00:00Z', lastUsedAt: null, expiresAt: '2026-12-02T10:00:00Z', current: false },
];
const MC = { mcUuid: '11111111-1111-1111-1111-111111111111', mcName: 'AnnaCraft', linkedAt: '2026-11-03T10:00:00Z' };

interface Setup {
  providers?: string[];
  methods?: { provider: string; since: string }[];
  minecraft?: (typeof MC)[];
  query?: Record<string, string>;
}

describe('Account page', () => {
  let fixture: ComponentFixture<Account>;
  let http: HttpTestingController;
  let root: HTMLElement;
  let navigated: string[];

  const text = (id: string) => root.querySelector(`[data-testid="${id}"]`)?.textContent?.trim();
  const has = (id: string) => root.querySelector(`[data-testid="${id}"]`) !== null;
  const settle = () => flushUi(fixture);

  async function open(setup: Setup = {}): Promise<void> {
    navigated = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'account', component: Blank }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NAVIGATE, useValue: (url: string) => navigated.push(url) },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(setup.query ?? {}) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login('anna@example.test', 'pw').subscribe();
    http.expectOne('/api/v1/auth/login').flush({ accessToken: 'T', tokenType: 'Bearer', expiresIn: 900, user: USER });
    await TestBed.inject(Router).navigateByUrl('/account');

    fixture = TestBed.createComponent(Account);
    root = fixture.nativeElement;
    http.expectOne('/api/v1/me/sessions').flush(SESSIONS);
    http.expectOne('/api/v1/me/minecraft').flush(setup.minecraft ?? []);
    http.expectOne('/api/v1/auth/oauth/providers').flush({ providers: setup.providers ?? [] });
    http.expectOne('/api/v1/me/identities').flush(setup.methods ?? [{ provider: 'password', since: '2026-11-01T10:00:00Z' }]);
    await settle();
  }

  afterEach(() => http.verify());

  describe('preferences', () => {
    it('shows the e-mail address and the display name', async () => {
      await open();
      expect(text('email')).toBe('anna@example.test');
      expect((root.querySelector('[data-testid="display-name"]') as HTMLInputElement).value).toBe('Anna');
    });

    it('saves a new display name', async () => {
      await open();
      const input = root.querySelector('[data-testid="display-name"]') as HTMLInputElement;
      input.value = '  Ana Maria ';
      input.dispatchEvent(new Event('input'));
      press(root, 'save-name');
      const patch = http.expectOne('/api/v1/me');
      expect(patch.request.method).toBe('PATCH');
      expect(patch.request.body).toEqual({ displayName: 'Ana Maria' });
      patch.flush({ ...USER, displayName: 'Ana Maria' });
      await settle();
      expect(has('saved')).toBeTrue();
      expect(TestBed.inject(AuthService).user()?.displayName).toBe('Ana Maria');
    });

    it('does not save an empty display name', async () => {
      await open();
      const input = root.querySelector('[data-testid="display-name"]') as HTMLInputElement;
      input.value = '   ';
      input.dispatchEvent(new Event('input'));
      press(root, 'save-name');
      await settle();
      http.expectNone('/api/v1/me');
      expect(root.querySelector('mat-error')).not.toBeNull();
    });

    it('changes the theme and saves it on the account', async () => {
      await open();
      press(root, 'theme-dark');
      const patch = http.expectOne('/api/v1/me');
      expect(patch.request.body).toEqual({ theme: 'dark' });
      patch.flush({ ...USER, theme: 'dark' });
      await settle();
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
      press(root, 'theme-system'); // leave the page as it was for the other tests
      http.expectOne('/api/v1/me').flush(USER);
    });

    it('saves the language, then opens the same page in the other build', async () => {
      await open();
      press(root, 'language-pt');
      const patch = http.expectOne('/api/v1/me');
      expect(patch.request.body).toEqual({ locale: 'pt' });
      patch.flush({ ...USER, locale: 'pt' });
      await settle();
      expect(navigated).toEqual(['/pt/account']);
    });

    it('does nothing when the language chosen is the one already shown', async () => {
      await open();
      press(root, 'language-en');
      await settle();
      http.expectNone('/api/v1/me');
      expect(navigated).toEqual([]);
    });
  });

  describe('sessions', () => {
    it('lists the logins, marks this device and lets the others be ended', async () => {
      await open();
      expect(text('session-s1')).toContain('Firefox on Linux');
      expect(text('session-s1')).toContain('(this device)');
      expect(has('end-s1')).toBeFalse(); // this one is ended with "Log out"
      expect(text('session-s2')).toContain('Chrome on Android');

      press(root, 'end-s2');
      const del = http.expectOne('/api/v1/me/sessions/s2');
      expect(del.request.method).toBe('DELETE');
      del.flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      expect(has('session-s2')).toBeFalse();
      expect(has('session-s1')).toBeTrue();
    });
  });

  describe('Minecraft accounts', () => {
    it('says so when none is linked', async () => {
      await open();
      expect(has('no-minecraft')).toBeTrue();
    });

    it('links an account with the code from Minecraft', async () => {
      await open();
      const input = root.querySelector('[data-testid="link-code"]') as HTMLInputElement;
      input.value = ' ABCD2345 ';
      input.dispatchEvent(new Event('input'));
      press(root, 'link');
      const post = http.expectOne('/api/v1/me/minecraft/link');
      expect(post.request.body).toEqual({ code: 'ABCD2345' });
      post.flush(MC);
      await settle();
      expect(text('mc-AnnaCraft')).toContain('AnnaCraft');
      expect(text('linked')).toContain('AnnaCraft');
      expect(has('no-minecraft')).toBeFalse();
    });

    it('explains a wrong code in words', async () => {
      await open();
      const input = root.querySelector('[data-testid="link-code"]') as HTMLInputElement;
      input.value = 'WRONG';
      input.dispatchEvent(new Event('input'));
      press(root, 'link');
      http.expectOne('/api/v1/me/minecraft/link').flush({ code: 'INVALID_CODE', message: 'x' }, { status: 400, statusText: 'Bad Request' });
      await settle();
      expect(text('failure')).toBe('This code is not right, or it was used already.');
    });

    it('asks for a code first when the box is empty', async () => {
      await open();
      press(root, 'link');
      await settle();
      http.expectNone('/api/v1/me/minecraft/link');
      expect(text('failure')).toBe('Enter the code that Minecraft showed.');
    });

    it('shows a code to type in Minecraft', async () => {
      await open();
      press(root, 'new-code');
      http.expectOne('/api/v1/me/minecraft/link-code').flush({ code: 'QWER5678', expiresAt: '2026-11-09T12:10:00Z' });
      await settle();
      expect(text('issued-code')).toBe('QWER5678');
    });

    it('unlinks an account', async () => {
      await open({ minecraft: [MC] });
      expect(has('mc-AnnaCraft')).toBeTrue();
      press(root, 'unlink-AnnaCraft');
      const del = http.expectOne(`/api/v1/me/minecraft/${MC.mcUuid}`);
      expect(del.request.method).toBe('DELETE');
      del.flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      expect(has('mc-AnnaCraft')).toBeFalse();
      expect(has('no-minecraft')).toBeTrue();
    });
  });

  describe('Discord', () => {
    it('is not shown when Discord is not set up and not linked', async () => {
      await open();
      expect(has('discord-link')).toBeFalse();
      expect(has('discord-linked')).toBeFalse();
    });

    it('offers to link when Discord is set up, and sends the browser where the API says', async () => {
      await open({ providers: ['discord'] });
      expect(has('discord-not-linked')).toBeTrue();
      press(root, 'discord-link');
      const post = http.expectOne('/api/v1/me/identities/discord/link');
      expect(post.request.method).toBe('POST');
      post.flush({ url: 'https://discord.example/authorize?state=abc' });
      await settle();
      expect(navigated).toEqual(['https://discord.example/authorize?state=abc']);
    });

    it('offers to unlink when linked, even if Discord is switched off on the site', async () => {
      await open({ methods: [{ provider: 'password', since: 'x' }, { provider: 'discord', since: 'x' }] });
      expect(has('discord-linked')).toBeTrue();
      press(root, 'discord-unlink');
      http.expectOne('/api/v1/me/identities/discord').flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      expect(has('discord-not-linked')).toBeTrue();
    });

    it('explains why the only login method cannot be unlinked', async () => {
      await open({ methods: [{ provider: 'discord', since: 'x' }] });
      press(root, 'discord-unlink');
      http.expectOne('/api/v1/me/identities/discord').flush({ code: 'LAST_LOGIN_METHOD', message: 'x' }, { status: 409, statusText: 'Conflict' });
      await settle();
      expect(text('failure')).toContain('only way you can log in');
      expect(has('discord-linked')).toBeTrue();
    });
  });

  describe('coming back from Discord', () => {
    it('says that Discord is linked', async () => {
      await open({ query: { oauth: 'linked' } });
      expect(text('oauth-notice')).toBe('Discord is linked to your account.');
    });

    it('says why linking failed', async () => {
      await open({ query: { oauth: 'error', code: 'DISCORD_ALREADY_LINKED' } });
      expect(text('oauth-notice')).toContain('belongs to another PokeDnD account');
    });

    it('says nothing when the address has no news', async () => {
      await open();
      expect(has('oauth-notice')).toBeFalse();
    });
  });
});
