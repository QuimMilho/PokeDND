// Phase 25: register, log in and log out against the running API.
import { expect, test } from '@playwright/test';
import { PASSWORD, createAccount, logIn, newEmail, site } from './helpers';

test('register, stay signed in after a reload, log out, log in again', async ({ page }) => {
  const email = newEmail();

  // not signed in: a private page sends the person to the login screen, and remembers where they wanted to go
  await page.goto(`${site()}/en/cards`);
  await expect(page).toHaveURL(/\/en\/login\?returnUrl=%2Fcards$/);

  // register
  await page.getByRole('link', { name: 'No account yet? Sign up' }).click();
  await expect(page).toHaveURL(/\/en\/register$/);
  await page.getByTestId('displayName').fill('Anna');
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill(PASSWORD);
  await page.getByTestId('submit').click();
  await expect(page.getByTestId('greeting')).toContainText('Hello, Anna');
  await expect(page.getByTestId('user-name')).toHaveText('Anna');

  // the page was opened again: the refresh cookie gives a new token, the person is still in
  await page.reload();
  await expect(page.getByTestId('greeting')).toContainText('Hello, Anna');
  const cookies = await page.context().cookies();
  const refresh = cookies.find((c) => c.name === 'pokednd_refresh');
  expect(refresh?.httpOnly).toBe(true);
  // nothing secret is kept where the page's scripts can read it
  const stored = await page.evaluate(() => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }) + document.cookie);
  expect(stored).not.toContain('pokednd_refresh');
  expect(stored).not.toContain('eyJ'); // the start of every signed token

  // log out
  await page.getByTestId('logout').click();
  await expect(page).toHaveURL(/\/en\/login$/);
  await page.goto(`${site()}/en/cards`);
  await expect(page).toHaveURL(/\/en\/login\?returnUrl=%2Fcards$/);

  // log in again, and land on the page that was asked for
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill(PASSWORD);
  await page.getByTestId('submit').click();
  await expect(page).toHaveURL(/\/en\/cards$/);
  await expect(page.getByTestId('logout')).toBeVisible();
});

test('a wrong password and a taken e-mail address are explained', async ({ page, request }) => {
  const email = newEmail();
  await createAccount(request, email);

  await page.goto(`${site()}/en/login`);
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill('not the password at all');
  await page.getByTestId('submit').click();
  await expect(page.getByTestId('failure')).toHaveText('The e-mail address or the password is wrong.');
  await expect(page).toHaveURL(/\/en\/login$/);

  await page.goto(`${site()}/en/register`);
  await page.getByTestId('displayName').fill('Anna again');
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill(PASSWORD);
  await page.getByTestId('submit').click();
  await expect(page.getByTestId('failure')).toHaveText('There is already an account with this e-mail address.');
});

test('a signed-in person is sent away from the login screen', async ({ page, request }) => {
  const email = newEmail();
  await createAccount(request, email);
  await logIn(page, email);
  await page.goto(`${site()}/en/login`);
  await expect(page.getByTestId('greeting')).toBeVisible();
});

test('the Portuguese build shows its forms and messages in Portuguese, and the language link keeps the page', async ({ page }) => {
  await page.goto(`${site()}/pt/cards`);
  await expect(page).toHaveURL(/\/pt\/login\?returnUrl=%2Fcards$/);
  await expect(page.getByText('Iniciar sessão').first()).toBeVisible();
  await page.getByTestId('submit').click();
  await expect(page.locator('mat-error')).toHaveText(['Introduza o seu endereço de e-mail.', 'Introduza uma palavra-passe.']);

  // the English link goes to the same page in the English build
  await page.getByRole('link', { name: 'EN' }).click();
  await expect(page).toHaveURL(/\/en\/login/);
  await page.getByTestId('submit').click();
  await expect(page.locator('mat-error')).toHaveText(['Enter your e-mail address.', 'Enter a password.']);
});

test('the Portuguese build registers a person with a Portuguese language preference', async ({ page, request }) => {
  const email = newEmail('pt');
  await page.goto(`${site()}/pt/register`);
  await page.getByTestId('displayName').fill('Ana');
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill(PASSWORD);
  await page.getByTestId('submit').click();
  await expect(page.getByTestId('greeting')).toContainText('Olá, Ana');
  const login = await request.post(`${site()}/api/v1/auth/login`, { data: { email, password: PASSWORD } });
  expect((await login.json()).user.locale).toBe('pt');
});
