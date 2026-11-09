// Phase 35: the shell (menu, themes, language) and the account page, against the real API.
import { expect, test } from '@playwright/test';
import { accessToken, createAccount, createServer, logIn, newEmail, site } from './helpers';

const MC_UUID = '11111111-2222-3333-4444-555555555555';

test('the account page changes the theme and the language, and both are saved on the account', async ({ page, request, browser }) => {
  const email = newEmail('shell');
  await createAccount(request, email, 'Anna');
  await logIn(page, email);

  // the menu leads to the account page
  await page.getByTestId('nav-account').click();
  await expect(page).toHaveURL(/\/en\/account$/);
  await expect(page.getByRole('heading', { name: 'Your account' })).toBeVisible();
  await expect(page.getByTestId('email')).toHaveText(email);
  await expect(page.getByTestId('nav-account')).toHaveAttribute('aria-current', 'page');

  // --- theme: the colours really change, and the choice is kept
  const scheme = () => page.evaluate(() => getComputedStyle(document.documentElement).colorScheme);
  const background = () => page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  await page.getByTestId('theme-light').click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  expect(await scheme()).toBe('light');
  const lightBackground = await background();
  await page.getByTestId('theme-dark').click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  expect(await scheme()).toBe('dark');
  expect(await background()).not.toBe(lightBackground);

  const token = await accessToken(request, email);
  await expect
    .poll(async () => (await (await request.get(`${site()}/api/v1/me`, { headers: { Authorization: `Bearer ${token}` } })).json()).theme)
    .toBe('dark');

  // after a reload the theme is still dark; so it is on another device where this browser's memory does not exist
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  const other = await browser.newContext();
  const otherPage = await other.newPage();
  await logIn(otherPage, email);
  await expect(otherPage.locator('html')).toHaveAttribute('data-theme', 'dark');
  await other.close();

  // --- language: the same page opens in the other build, and the choice is saved
  await page.getByTestId('language-pt').click();
  await expect(page).toHaveURL(/\/pt\/account$/);
  await expect(page.getByRole('heading', { name: 'A sua conta' })).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark'); // the theme came along
  const me = await (await request.get(`${site()}/api/v1/me`, { headers: { Authorization: `Bearer ${token}` } })).json();
  expect(me.locale).toBe('pt');
  await page.getByTestId('language-en').click();
  await expect(page).toHaveURL(/\/en\/account$/);
});

test('a Minecraft account is linked with a code from the mod, and the other way round', async ({ page, request }) => {
  const email = newEmail('mc');
  await createAccount(request, email, 'Anna');
  const server = await createServer(request);
  const mod = { Authorization: `Bearer ${server.token}` };
  await logIn(page, email);
  await page.goto(`${site()}/en/account`);
  await expect(page.getByTestId('no-minecraft')).toBeVisible();

  // the mod prints a code (what /pokednd link does), the player types it in the app
  const made = await request.post(`${site()}/api/v1/mod/link-code`, { data: { mcUuid: MC_UUID, mcName: 'AnnaCraft' }, headers: mod });
  expect(made.status()).toBe(200);
  const { code } = await made.json();
  await page.getByTestId('link-code').fill(code);
  await page.getByTestId('link').click();
  await expect(page.getByTestId('mc-AnnaCraft')).toBeVisible();
  await expect(page.getByTestId('linked')).toContainText('AnnaCraft');

  // a wrong code is explained
  await page.getByTestId('link-code').fill('ZZZZZZZZ');
  await page.getByTestId('link').click();
  await expect(page.getByTestId('failure')).toHaveText('This code is not right, or it was used already.');

  // the other way round: the app shows a code and the player types it in Minecraft
  await page.getByTestId('new-code').click();
  const issued = (await page.getByTestId('issued-code').textContent())!.trim();
  const done = await request.post(`${site()}/api/v1/mod/link-complete`, {
    data: { code: issued, mcUuid: '66666666-7777-8888-9999-000000000000', mcName: 'AnnaBuild' },
    headers: mod,
  });
  expect(done.status()).toBe(200);
  await page.reload();
  await expect(page.getByTestId('mc-AnnaBuild')).toBeVisible();
  await expect(page.getByTestId('mc-AnnaCraft')).toBeVisible();

  // unlink one
  await page.getByTestId('unlink-AnnaCraft').click();
  await expect(page.getByTestId('mc-AnnaCraft')).toHaveCount(0);
  await expect(page.getByTestId('mc-AnnaBuild')).toBeVisible();
});

test('sessions: this device is marked and another login can be ended', async ({ page, request }) => {
  const email = newEmail('sessions');
  await createAccount(request, email, 'Anna');
  await accessToken(request, email); // a second login, from the test
  await logIn(page, email);
  await page.goto(`${site()}/en/account`);
  await expect(page.getByTestId('this-device')).toBeVisible();
  const others = page.locator('[data-testid^="end-"]');
  await expect(others.first()).toBeVisible();
  const before = await others.count();
  await others.first().click();
  await expect(others).toHaveCount(before - 1);
});

test('on a phone the menu is a bar at the bottom and the page does not scroll sideways', async ({ page, request }) => {
  const email = newEmail('phone');
  await createAccount(request, email, 'Anna');
  await page.setViewportSize({ width: 390, height: 800 });
  await logIn(page, email);
  for (const route of ['games', 'account', 'cards']) {
    await page.goto(`${site()}/en/${route}`);
    await expect(page.getByTestId('nav-account')).toBeVisible();
    const box = (await page.getByTestId('nav-account').boundingBox())!;
    expect(box.y + box.height).toBeGreaterThan(740); // at the bottom of the screen
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow, `sideways scroll on /${route}`).toBeLessThanOrEqual(0);
  }
  // on a wide screen the same menu is in the top bar
  await page.setViewportSize({ width: 1200, height: 800 });
  const box = (await page.getByTestId('nav-account').boundingBox())!;
  expect(box.y).toBeLessThan(80);
});

test('the login screen has no Discord button while Discord is not set up', async ({ page }) => {
  await page.goto(`${site()}/en/login`);
  await expect(page.getByTestId('submit')).toBeVisible();
  await expect(page.getByTestId('discord')).toHaveCount(0);
});

test('a person without a server does not see "New game" in the menu', async ({ page, request }) => {
  const email = newEmail('plain');
  await createAccount(request, email, 'Anna');
  await logIn(page, email);
  await expect(page.getByTestId('nav-games')).toBeVisible();
  await expect(page.getByTestId('nav-new-game')).toHaveCount(0);
});
