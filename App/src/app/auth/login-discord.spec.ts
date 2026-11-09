import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { flushUi } from '../../testing/ui';
import { Login } from './login';

async function open(providers: string[] | 'error', query: Record<string, string> = {}) {
  TestBed.configureTestingModule({
    imports: [Login],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
    ],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(Login);
  fixture.detectChanges();
  const request = http.expectOne('/api/v1/auth/oauth/providers');
  if (providers === 'error') {
    request.error(new ProgressEvent('error'));
  } else {
    request.flush({ providers });
  }
  await flushUi(fixture);
  return { root: fixture.nativeElement as HTMLElement, http };
}

describe('Login screen: Sign in with Discord', () => {
  it('shows the button when Discord is set up, and it goes to the API, which starts the redirect', async () => {
    const { root } = await open(['discord']);
    const button = root.querySelector('[data-testid="discord"]') as HTMLAnchorElement;
    expect(button).not.toBeNull();
    expect(button.textContent).toContain('Sign in with Discord');
    expect(button.getAttribute('href')).toBe('/api/v1/auth/oauth/discord/start');
  });

  it('leaves the button out when no provider is set up', async () => {
    const { root } = await open([]);
    expect(root.querySelector('[data-testid="discord"]')).toBeNull();
  });

  it('leaves the button out, and still works, when the list of providers cannot be read', async () => {
    const { root } = await open('error');
    expect(root.querySelector('[data-testid="discord"]')).toBeNull();
    expect(root.querySelector('[data-testid="submit"]')).not.toBeNull();
  });

  it('explains it when the person comes back from Discord with a problem', async () => {
    const { root } = await open(['discord'], { oauth: 'error', code: 'OAUTH_DENIED' });
    expect(root.querySelector('[data-testid="failure"]')?.textContent).toContain('You did not allow PokeDnD to use your Discord account.');
  });

  it('says nothing special for a normal visit', async () => {
    const { root } = await open(['discord']);
    expect(root.querySelector('[data-testid="failure"]')).toBeNull();
  });
});
