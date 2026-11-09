import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { flushUi, nextRequest } from '../testing/ui';
import { App } from './app';
import { routes } from './app.routes';

const USER = { id: '1', email: 'anna@example.test', emailVerified: true, displayName: 'Anna', locale: 'en', theme: 'system', siteRole: 'user' };

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows the app name and the language links, which stay on the page the person is on', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    const navigation = router.navigateByUrl('/login');
    (await nextRequest(TestBed.inject(HttpTestingController), '/api/v1/auth/refresh')).flush({}, { status: 401, statusText: 'Unauthorized' });
    await navigation;
    await flushUi(fixture);
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('PokeDnD');
    const hrefs = Array.from(root.querySelectorAll('nav a')).map((a) => a.getAttribute('href'));
    expect(hrefs).toEqual(['/en/login', '/pt/login']);
  });

  it('sends a person who is not signed in from the home page to the login screen', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    const navigation = router.navigateByUrl('/');
    (await nextRequest(TestBed.inject(HttpTestingController), '/api/v1/auth/refresh')).flush({}, { status: 401, statusText: 'Unauthorized' });
    await navigation;
    await flushUi(fixture);
    expect(router.url).toBe('/login?returnUrl=%2F');
  });

  it('shows the name and a log-out button to a signed-in person', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    const navigation = router.navigateByUrl('/');
    (await nextRequest(TestBed.inject(HttpTestingController), '/api/v1/auth/refresh')).flush({ accessToken: 'T', tokenType: 'Bearer', expiresIn: 900, user: USER });
    await navigation;
    await flushUi(fixture);
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="user-name"]')?.textContent).toContain('Anna');
    expect(root.querySelector('[data-testid="logout"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="greeting"]')?.textContent).toContain('Hello, Anna');
  });
});
