import { APIRequestContext, Page, expect } from '@playwright/test';

export const PASSWORD = 'a long enough password';

/** The address of the web server that `global-setup.mjs` started (the app and, behind /api, the API). */
export function site(): string {
  const url = process.env['POKEDND_E2E_URL'];
  if (!url) throw new Error('POKEDND_E2E_URL is not set: the global setup did not run');
  return url;
}

let counter = 0;

/** A new e-mail address for every test, so that tests never meet each other's accounts. */
export function newEmail(name = 'player'): string {
  counter += 1;
  return `${name}-${Date.now()}-${counter}@example.test`;
}

/** Makes an account through the API (quick). */
export async function createAccount(request: APIRequestContext, email: string, displayName = 'Anna'): Promise<void> {
  const answer = await request.post(`${site()}/api/v1/auth/register`, { data: { email, password: PASSWORD, displayName, locale: 'en' } });
  expect(answer.status()).toBe(201);
}

/** The access token of an account, to call the API directly from the test. */
export async function accessToken(request: APIRequestContext, email: string): Promise<string> {
  const answer = await request.post(`${site()}/api/v1/auth/login`, { data: { email, password: PASSWORD } });
  expect(answer.status()).toBe(200);
  return (await answer.json()).accessToken;
}

/** Logs in through the login screen. */
export async function logIn(page: Page, email: string, language = 'en'): Promise<void> {
  await page.goto(`${site()}/${language}/login`);
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill(PASSWORD);
  await page.getByTestId('submit').click();
  await expect(page.getByTestId('logout')).toBeVisible();
}

/** The site admin that the end-to-end stack creates at start (see `stack.mjs`). */
export const ADMIN_EMAIL = 'admin@example.test';

let serverCounter = 0;

/** A Minecraft server registered through the API by the site admin. `token` is what the mod would use. */
export async function createServer(request: APIRequestContext, name = 'Survival'): Promise<{ id: string; name: string; token: string }> {
  serverCounter += 1;
  const unique = `${name} ${Date.now()}-${serverCounter}`;
  const admin = await accessToken(request, ADMIN_EMAIL);
  const answer = await request.post(`${site()}/api/v1/servers`, { data: { name: unique }, headers: { Authorization: `Bearer ${admin}` } });
  expect(answer.status()).toBe(201);
  const body = await answer.json();
  return { id: body.server.id, name: unique, token: body.token };
}

/** Logs in through the login screen as the site admin. */
export async function logInAsAdmin(page: Page, language = 'en'): Promise<void> {
  await logIn(page, ADMIN_EMAIL, language);
}
