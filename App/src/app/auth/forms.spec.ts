import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { useEnglish, usePortuguese } from '../../testing/translations';
import { flushUi } from '../../testing/ui';
import { Login } from './login';
import { Register } from './register';

const USER = { id: '1', email: 'anna@example.test', emailVerified: true, displayName: 'Anna', locale: 'en', theme: 'system', siteRole: 'user' };

function setup<T>(component: new () => T) {
  TestBed.configureTestingModule({
    imports: [component],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const fixture = TestBed.createComponent(component);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  const type = (testId: string, value: string) => {
    const input = root.querySelector<HTMLInputElement>(`[data-testid="${testId}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  };
  const submit = async () => {
    root.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await flushUi(fixture);
  };
  const errors = () => Array.from(root.querySelectorAll('mat-error')).map((e) => e.textContent?.trim());
  const failure = () => root.querySelector('[data-testid="failure"]')?.textContent?.trim();
  return { fixture, type, submit, errors, failure, http: TestBed.inject(HttpTestingController) };
}

describe('Login form', () => {
  afterEach(() => useEnglish());

  it('asks for the e-mail address and the password when they are empty (English)', async () => {
    const { submit, errors, http } = setup(Login);
    await submit();
    expect(errors()).toEqual(['Enter your e-mail address.', 'Enter a password.']);
    http.expectNone('/api/v1/auth/login');
  });

  it('says so when the e-mail address does not look right (English)', async () => {
    const { type, submit, errors } = setup(Login);
    type('email', 'not-an-address');
    type('password', 'whatever');
    await submit();
    expect(errors()).toEqual(['This does not look like an e-mail address.']);
  });

  it('shows the same messages in Portuguese', async () => {
    usePortuguese();
    const { type, submit, errors } = setup(Login);
    await submit();
    expect(errors()).toEqual(['Introduza o seu endereço de e-mail.', 'Introduza uma palavra-passe.']);
    type('email', 'not-an-address');
    await submit();
    expect(errors()[0]).toBe('Isto não parece um endereço de e-mail.');
  });

  it('sends the login and goes home on success, trimming spaces around the e-mail address', async () => {
    const { type, submit, http } = setup(Login);
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    type('email', '  anna@example.test ');
    type('password', 'a long enough password');
    await submit();
    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.body).toEqual({ email: 'anna@example.test', password: 'a long enough password' });
    request.flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900, user: USER });
    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('shows the answer to a wrong password in the language of the build, not the server text', async () => {
    const { type, submit, failure, fixture, http } = setup(Login);
    type('email', 'anna@example.test');
    type('password', 'wrong password');
    await submit();
    http.expectOne('/api/v1/auth/login').flush({ code: 'INVALID_CREDENTIALS', message: 'server text' }, { status: 401, statusText: 'Unauthorized' });
    await flushUi(fixture);
    expect(failure()).toBe('The e-mail address or the password is wrong.');
  });

  it('shows the wrong-password answer in Portuguese too', async () => {
    usePortuguese();
    const { type, submit, failure, fixture, http } = setup(Login);
    type('email', 'anna@example.test');
    type('password', 'wrong password');
    await submit();
    http.expectOne('/api/v1/auth/login').flush({ code: 'INVALID_CREDENTIALS', message: 'server text' }, { status: 401, statusText: 'Unauthorized' });
    await flushUi(fixture);
    expect(failure()).toBe('O endereço de e-mail ou a palavra-passe estão errados.');
  });
});

describe('Register form', () => {
  afterEach(() => useEnglish());

  it('checks the display name, the e-mail address and the length of the password (English)', async () => {
    const { type, submit, errors } = setup(Register);
    await submit();
    expect(errors()).toEqual(['Enter a display name.', 'Enter your e-mail address.', 'Enter a password.']);
    type('displayName', 'x'.repeat(41));
    type('password', 'short');
    await submit();
    expect(errors()).toContain('The display name can have at most 40 characters.');
    expect(errors()).toContain('The password needs at least 10 characters.');
    type('password', 'p'.repeat(129));
    await submit();
    expect(errors()).toContain('The password can have at most 128 characters.');
  });

  it('shows the same messages in Portuguese', async () => {
    usePortuguese();
    const { type, submit, errors } = setup(Register);
    await submit();
    expect(errors()).toEqual(['Introduza um nome de apresentação.', 'Introduza o seu endereço de e-mail.', 'Introduza uma palavra-passe.']);
    type('password', 'short');
    await submit();
    expect(errors()).toContain('A palavra-passe precisa de pelo menos 10 caracteres.');
    type('displayName', 'x'.repeat(41));
    await submit();
    expect(errors()).toContain('O nome de apresentação pode ter no máximo 40 caracteres.');
  });

  it('creates the account, then logs in with it', async () => {
    const { type, submit, http } = setup(Register);
    spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    type('displayName', 'Anna');
    type('email', 'anna@example.test');
    type('password', 'a long enough password');
    await submit();
    const register = http.expectOne('/api/v1/auth/register');
    expect(register.request.body).toEqual({ email: 'anna@example.test', password: 'a long enough password', displayName: 'Anna', locale: 'en' });
    register.flush(USER, { status: 201, statusText: 'Created' });
    http.expectOne('/api/v1/auth/login').flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900, user: USER });
  });

  it('tells the person when the e-mail address is already used', async () => {
    const { type, submit, failure, fixture, http } = setup(Register);
    type('displayName', 'Anna');
    type('email', 'anna@example.test');
    type('password', 'a long enough password');
    await submit();
    http.expectOne('/api/v1/auth/register').flush({ code: 'EMAIL_TAKEN', message: 'x' }, { status: 409, statusText: 'Conflict' });
    await flushUi(fixture);
    expect(failure()).toBe('There is already an account with this e-mail address.');
  });
});
