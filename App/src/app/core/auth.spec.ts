import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { publicOnlyGuard, signedInGuard } from './auth.guards';
import { AuthService } from './auth.service';

const USER = { id: '1', email: 'anna@example.test', emailVerified: true, displayName: 'Anna', locale: 'en', theme: 'system', siteRole: 'user' };
const tokens = (accessToken: string) => ({ accessToken, tokenType: 'Bearer', expiresIn: 900, user: USER });
const unauthorised = { status: 401, statusText: 'Unauthorized' };

function setup() {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
  });
  return {
    auth: TestBed.inject(AuthService),
    http: TestBed.inject(HttpClient),
    mock: TestBed.inject(HttpTestingController),
    router: TestBed.inject(Router),
  };
}

describe('API client (token handling)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('logs in, keeps the token in memory and sends it with the next call', () => {
    const { auth, http, mock } = setup();
    auth.login('anna@example.test', 'pw').subscribe();
    mock.expectOne('/api/v1/auth/login').flush(tokens('T1'));
    expect(auth.signedIn()).toBeTrue();
    expect(auth.user()?.displayName).toBe('Anna');

    http.get('/api/v1/data/species').subscribe();
    expect(mock.expectOne('/api/v1/data/species').request.headers.get('Authorization')).toBe('Bearer T1');
  });

  it('does not store the token anywhere in the browser', () => {
    const { auth, mock } = setup();
    auth.login('anna@example.test', 'pw').subscribe();
    mock.expectOne('/api/v1/auth/login').flush(tokens('SECRET-TOKEN'));
    const everything = JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }) + document.cookie;
    expect(everything).not.toContain('SECRET-TOKEN');
  });

  it('on a 401 asks for a new token once and repeats the call once', () => {
    const { auth, http, mock } = setup();
    auth.login('anna@example.test', 'pw').subscribe();
    mock.expectOne('/api/v1/auth/login').flush(tokens('OLD'));

    let answer: unknown;
    http.get('/api/v1/cards/exports').subscribe((a) => (answer = a));
    mock.expectOne('/api/v1/cards/exports').flush({ code: 'INVALID_TOKEN' }, unauthorised);
    mock.expectOne('/api/v1/auth/refresh').flush(tokens('NEW'));
    const retry = mock.expectOne('/api/v1/cards/exports');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer NEW');
    retry.flush(['ok']);
    expect(answer).toEqual(['ok']);
    expect(auth.accessToken()).toBe('NEW');
  });

  it('gives up after one retry: a second 401 is the answer', () => {
    const { auth, http, mock } = setup();
    auth.login('anna@example.test', 'pw').subscribe();
    mock.expectOne('/api/v1/auth/login').flush(tokens('OLD'));

    let status = 0;
    http.get('/api/v1/cards/exports').subscribe({ error: (e) => (status = e.status) });
    mock.expectOne('/api/v1/cards/exports').flush({}, unauthorised);
    mock.expectOne('/api/v1/auth/refresh').flush(tokens('NEW'));
    mock.expectOne('/api/v1/cards/exports').flush({}, unauthorised);
    mock.expectNone('/api/v1/auth/refresh');
    expect(status).toBe(401);
  });

  it('when the refresh fails, signs the person out and goes to the login screen', () => {
    const { auth, http, mock, router } = setup();
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);
    auth.login('anna@example.test', 'pw').subscribe();
    mock.expectOne('/api/v1/auth/login').flush(tokens('OLD'));

    let status = 0;
    http.get('/api/v1/cards/exports').subscribe({ error: (e) => (status = e.status) });
    mock.expectOne('/api/v1/cards/exports').flush({}, unauthorised);
    mock.expectOne('/api/v1/auth/refresh').flush({ code: 'INVALID_REFRESH_TOKEN' }, unauthorised);
    expect(status).toBe(401);
    expect(auth.status()).toBe('signed-out');
    expect(auth.user()).toBeNull();
    expect(auth.accessToken()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], jasmine.anything());
  });

  it('several calls that fail together share one refresh (the refresh token can be used only once)', () => {
    const { auth, http, mock } = setup();
    auth.login('anna@example.test', 'pw').subscribe();
    mock.expectOne('/api/v1/auth/login').flush(tokens('OLD'));

    const answers: unknown[] = [];
    http.get('/api/v1/a').subscribe((a) => answers.push(a));
    http.get('/api/v1/b').subscribe((a) => answers.push(a));
    mock.expectOne('/api/v1/a').flush({}, unauthorised);
    mock.expectOne('/api/v1/b').flush({}, unauthorised);
    mock.expectOne('/api/v1/auth/refresh').flush(tokens('NEW')); // exactly one
    mock.expectOne('/api/v1/a').flush('A');
    mock.expectOne('/api/v1/b').flush('B');
    expect(answers).toEqual(['A', 'B']);
  });

  it('does not try to refresh when a login itself is refused', () => {
    const { auth, mock } = setup();
    let status = 0;
    auth.login('anna@example.test', 'wrong').subscribe({ error: (e) => (status = e.status) });
    mock.expectOne('/api/v1/auth/login').flush({ code: 'INVALID_CREDENTIALS' }, unauthorised);
    mock.expectNone('/api/v1/auth/refresh');
    expect(status).toBe(401);
  });

  it('leaves calls to other addresses alone', () => {
    const { auth, http, mock } = setup();
    auth.login('anna@example.test', 'pw').subscribe();
    mock.expectOne('/api/v1/auth/login').flush(tokens('T1'));
    http.get('https://elsewhere.example/api').subscribe();
    expect(mock.expectOne('https://elsewhere.example/api').request.headers.has('Authorization')).toBeFalse();
  });

  it('logout forgets the token even when the server cannot be reached', () => {
    const { auth, mock, router } = setup();
    spyOn(router, 'navigate').and.resolveTo(true);
    auth.login('anna@example.test', 'pw').subscribe();
    mock.expectOne('/api/v1/auth/login').flush(tokens('T1'));
    auth.logout().subscribe();
    mock.expectOne('/api/v1/auth/logout').error(new ProgressEvent('error'));
    expect(auth.signedIn()).toBeFalse();
    expect(auth.accessToken()).toBeNull();
  });
});

describe('Route guards', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = (url: string) => ({ url }) as RouterStateSnapshot;

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  async function run(guard: typeof signedInGuard, url: string, refresh: () => void) {
    const { mock } = setup();
    const result = TestBed.runInInjectionContext(() => guard(route, state(url))) as Promise<boolean | UrlTree>;
    refresh();
    return { result: await result, mock };
  }

  it('lets a signed-in person into a private page (the cookie gives a new token)', async () => {
    const { mock } = setup();
    const result = TestBed.runInInjectionContext(() => signedInGuard(route, state('/cards'))) as Promise<boolean | UrlTree>;
    mock.expectOne('/api/v1/auth/refresh').flush(tokens('T'));
    expect(await result).toBeTrue();
  });

  it('sends a person with no session to the login screen and remembers the page', async () => {
    const { result } = await run(signedInGuard, '/cards/export', () => TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/refresh').flush({}, unauthorised));
    expect(result instanceof UrlTree).toBeTrue();
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/login?returnUrl=%2Fcards%2Fexport');
  });

  it('asks the server only once, however many pages are opened', async () => {
    const { mock } = setup();
    const first = TestBed.runInInjectionContext(() => signedInGuard(route, state('/cards'))) as Promise<boolean | UrlTree>;
    const second = TestBed.runInInjectionContext(() => signedInGuard(route, state('/cards/export'))) as Promise<boolean | UrlTree>;
    mock.expectOne('/api/v1/auth/refresh').flush(tokens('T'));
    expect(await first).toBeTrue();
    expect(await second).toBeTrue();
  });

  it('keeps signed-in people away from login and register', async () => {
    const { mock } = setup();
    const result = TestBed.runInInjectionContext(() => publicOnlyGuard(route, state('/login'))) as Promise<boolean | UrlTree>;
    mock.expectOne('/api/v1/auth/refresh').flush(tokens('T'));
    const answer = await result;
    expect(answer instanceof UrlTree).toBeTrue();
    expect(TestBed.inject(Router).serializeUrl(answer as UrlTree)).toBe('/');
  });

  it('lets people without a session see login and register', async () => {
    const { result } = await run(publicOnlyGuard, '/login', () => TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/refresh').flush({}, unauthorised));
    expect(result).toBeTrue();
  });
});
