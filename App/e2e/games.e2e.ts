// Phase 36: an admin creates a game and opens the lobby, a second user joins and is accepted; the settings screen shows the whole registry.
import { Page, expect, test } from '@playwright/test';
import { ADMIN_EMAIL, accessToken, createAccount, createServer, logIn, logInAsAdmin, newEmail, site } from './helpers';

/** The admin makes a game on a new server and opens its lobby. Returns what the other tests need. */
async function gameWithOpenLobby(page: Page, serverName: string, gameName: string, preset = 'builtin:defaults') {
  await logInAsAdmin(page);
  await page.getByTestId('nav-games').click();
  await expect(page).toHaveURL(/\/en\/games$/);
  await page.getByTestId('new-game').click();
  await expect(page).toHaveURL(/\/en\/games\/new$/);

  await page.getByTestId('server').click();
  await page.getByTestId(`server-${serverName}`).click();
  await page.getByTestId('name').fill(gameName);
  await page.getByTestId('preset').click();
  await page.getByTestId(`preset-${preset}`).click();
  await page.getByTestId('create').click();

  await expect(page).toHaveURL(/\/en\/games\/[0-9a-f-]{36}$/);
  const gameId = page.url().split('/').pop()!;
  await expect(page.getByTestId('game-name')).toHaveText(gameName);
  await expect(page.getByTestId('game-state')).toHaveText('Draft');
  return gameId;
}

test('an admin creates a game and opens the lobby; a second user joins with the code and is accepted', async ({ page, request, browser }) => {
  const server = await createServer(request);
  const player = newEmail('player');
  await createAccount(request, player, 'Bruno');
  const gameId = await gameWithOpenLobby(page, server.name, 'Kanto Weekend', 'builtin:casual');

  // the draft: the buttons of a draft, and the settings are reachable
  await expect(page.getByTestId('action-open-lobby')).toBeVisible();
  await expect(page.getByTestId('to-settings')).toBeVisible();
  await page.getByTestId('action-open-lobby').click();
  await expect(page).toHaveURL(new RegExp(`/en/games/${gameId}/lobby$`));
  const code = (await page.getByTestId('join-code').textContent())!.trim();
  expect(code).toMatch(/^[A-Z0-9]{6}$/);
  await expect(page.getByTestId('no-requests')).toBeVisible();

  // the second user: not allowed to create games, joins with the code
  const other = await browser.newContext();
  const otherPage = await other.newPage();
  await logIn(otherPage, player);
  await expect(otherPage.getByTestId('nav-new-game')).toHaveCount(0);
  await otherPage.getByTestId('nav-games').click();
  await expect(otherPage.getByTestId('no-games')).toBeVisible();
  await otherPage.getByTestId('join-code').fill('ZZZZZZ');
  await otherPage.getByTestId('join').click();
  await expect(otherPage.getByTestId('failure')).toHaveText('This code is not right, or the lobby is closed.');
  await otherPage.getByTestId('join-code').fill(code.toLowerCase());
  await otherPage.getByTestId('join').click();
  await expect(otherPage.getByTestId('joined')).toContainText('Kanto Weekend');
  await expect(otherPage.getByTestId('waiting')).toHaveText('Waiting for the admin to accept you');

  // the admin sees the request (the lobby asks every few seconds) and accepts it
  await expect(page.getByTestId('request-Bruno')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId('request-Bruno')).toContainText('Player');
  await page.getByTestId('accept-Bruno').click();
  await expect(page.getByTestId('request-Bruno')).toHaveCount(0);
  await expect(page.getByTestId('member-Bruno')).toBeVisible();

  // the player now has the game: opens it and sees the lobby
  await otherPage.reload();
  await expect(otherPage.getByTestId('game-Kanto Weekend')).toContainText('Player');
  await otherPage.getByTestId('open-game').click();
  await expect(otherPage.getByTestId('game-state')).toHaveText('Lobby open');
  await expect(otherPage.getByTestId('action-start')).toHaveCount(0); // players have no admin buttons
  await otherPage.getByTestId('to-lobby').click();
  await expect(otherPage.getByTestId('member-Bruno')).toBeVisible();
  await expect(otherPage.getByTestId('join-code')).toHaveCount(0); // the code is for the admin
  await expect(otherPage.getByTestId('waiting-for-admin')).toBeVisible();

  // the admin starts the game: the player's lobby page notices
  await page.getByTestId('to-game').click();
  await page.getByTestId('action-start').click();
  await expect(page.getByTestId('game-state')).toHaveText('Running');
  await expect(otherPage.getByTestId('lobby-closed')).toBeVisible({ timeout: 20_000 });
  await other.close();
});

test('a request can be rejected, a spectator can ask, and a member can be removed', async ({ page, request, browser }) => {
  const server = await createServer(request);
  const rejected = newEmail('rejected');
  const watcher = newEmail('watcher');
  await createAccount(request, rejected, 'Carla');
  await createAccount(request, watcher, 'Dani');
  await gameWithOpenLobby(page, server.name, 'Johto Night');
  await page.getByTestId('action-open-lobby').click();
  const code = (await page.getByTestId('join-code').textContent())!.trim();

  const c1 = await browser.newContext();
  const p1 = await c1.newPage();
  await logIn(p1, rejected);
  await p1.goto(`${site()}/en/games`);
  await p1.getByTestId('join-code').fill(code);
  await p1.getByTestId('join').click();
  await expect(p1.getByTestId('joined')).toBeVisible();

  const c2 = await browser.newContext();
  const p2 = await c2.newPage();
  await logIn(p2, watcher);
  await p2.goto(`${site()}/en/games`);
  await p2.getByTestId('join-code').fill(code);
  await p2.getByTestId('role-spectator').click();
  await p2.getByTestId('join').click();
  await expect(p2.getByTestId('joined')).toBeVisible();

  await expect(page.getByTestId('request-Carla')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId('request-Dani')).toContainText('Spectator');
  await page.getByTestId('reject-Carla').click();
  await expect(page.getByTestId('request-Carla')).toHaveCount(0);
  await expect(page.getByTestId('member-Carla')).toHaveCount(0);
  await page.getByTestId('accept-Dani').click();
  await expect(page.getByTestId('member-Dani')).toContainText('Spectator');

  // removing a member takes a second tap
  await page.getByTestId('kick-Dani').click();
  await expect(page.getByTestId('member-Dani')).toBeVisible();
  await page.getByTestId('kick-Dani-yes').click();
  await expect(page.getByTestId('member-Dani')).toHaveCount(0);

  // the game leaves Carla's list: her request was not accepted
  await p1.reload();
  await expect(p1.getByTestId('no-games')).toBeVisible();
  await c1.close();
  await c2.close();
});

test('a game walks through its states with the buttons of each state', async ({ page, request }) => {
  const server = await createServer(request);
  await gameWithOpenLobby(page, server.name, 'Hoenn Run');
  await page.getByTestId('action-open-lobby').click();
  await page.getByTestId('to-game').click();
  await expect(page.getByTestId('game-state')).toHaveText('Lobby open');
  await page.getByTestId('action-start').click();
  await expect(page.getByTestId('game-state')).toHaveText('Running');
  await page.getByTestId('action-pause').click();
  await expect(page.getByTestId('game-state')).toHaveText('Paused');
  await page.getByTestId('action-resume').click();
  await expect(page.getByTestId('game-state')).toHaveText('Running');
  await page.getByTestId('action-end').click();
  await expect(page.getByTestId('action-end-yes')).toBeVisible(); // asks first
  await page.getByTestId('action-end-no').click();
  await expect(page.getByTestId('game-state')).toHaveText('Running');
  await page.getByTestId('action-end').click();
  await page.getByTestId('action-end-yes').click();
  await expect(page.getByTestId('game-state')).toHaveText('Ended');
  await page.getByTestId('action-archive').click();
  await page.getByTestId('action-archive-yes').click();
  await expect(page).toHaveURL(/\/en\/games$/);
  await expect(page.getByTestId('game-Hoenn Run')).toHaveCount(0); // archived games leave the list
});

test('the settings screen shows exactly the keys of the registry, with markers, reset, presets and search', async ({ page, request }) => {
  const server = await createServer(request);
  await gameWithOpenLobby(page, server.name, 'Sinnoh Trip', 'builtin:casual');
  await page.getByTestId('to-settings').click();
  await expect(page).toHaveURL(/\/en\/games\/[0-9a-f-]{36}\/admin\/settings$/);

  // --- every key of the registry, once
  const admin = await accessToken(request, ADMIN_EMAIL);
  const registry: { key: string }[] = await (await request.get(`${site()}/api/v1/settings/registry`, { headers: { Authorization: `Bearer ${admin}` } })).json();
  expect(registry.length).toBeGreaterThan(200);
  await expect(page.getByTestId('count')).toHaveText(new RegExp(`^${registry.length} of ${registry.length} settings shown`));
  await page.getByTestId('open-all').click();
  await expect(page.locator('app-setting-row')).toHaveCount(registry.length);
  const shown = await page.locator('app-setting-row [data-key]').evaluateAll((rows) => rows.map((r) => r.getAttribute('data-key')));
  expect(new Set(shown).size).toBe(registry.length);
  expect([...shown].sort()).toEqual(registry.map((s) => s.key).sort());

  // --- the preset "Casual" that the game started with: markers say where values come from
  await expect(page.getByTestId('rule-set-name')).toHaveText('Casual');
  await expect(page.getByTestId('source-combat.ai.level')).toHaveText('From the rule set');
  await expect(page.getByTestId('source-dice.style')).toHaveText('Default');

  // --- change a switch: it is saved, marked, and can be reset
  await page.getByTestId('search').fill('game.admin.plays');
  await expect(page.locator('app-setting-row')).toHaveCount(1);
  await expect(page.getByTestId('source-game.admin.plays')).toHaveText('Default');
  await page.getByTestId('input-game.admin.plays').click();
  await expect(page.getByTestId('source-game.admin.plays')).toHaveText('Changed in this game');
  await page.reload();
  await page.getByTestId('changed-only').click();
  await expect(page.locator('app-setting-row [data-key="game.admin.plays"]')).toHaveCount(1);
  await page.getByTestId('reset-game.admin.plays').click();
  await expect(page.locator('app-setting-row [data-key="game.admin.plays"]')).toHaveCount(0); // no longer changed, so it leaves the "changed only" list
  await page.getByTestId('changed-only').click();

  // --- a number out of range is stopped
  await page.getByTestId('search').fill('admin.undo.depth');
  await page.getByTestId('input-admin.undo.depth').fill('99999');
  await page.getByTestId('input-admin.undo.depth').press('Enter');
  await expect(page.getByTestId('error-admin.undo.depth')).toHaveText('The largest value is 1000.');
  await page.getByTestId('search').fill('');

  // --- load another preset: a preview first
  await page.getByTestId('preset-picker').selectOption('builtin:hardcore');
  await expect(page.getByTestId('preview')).toBeVisible();
  await expect(page.getByTestId('change-combat.ai.level')).toContainText('easy → hard');
  await page.getByTestId('apply').click();
  await expect(page.getByTestId('applied')).toContainText('Hardcore');
  await expect(page.getByTestId('rule-set-name')).toHaveText('Hardcore');
  await page.getByTestId('open-all').click();
  await expect(page.getByTestId('source-combat.ai.level')).toHaveText('From the rule set');
});

test('a player cannot change settings', async ({ page, request, browser }) => {
  const server = await createServer(request);
  const gameId = await gameWithOpenLobby(page, server.name, 'Kalos Tour');
  const player = newEmail('settings-player');
  await createAccount(request, player, 'Eva');
  await page.getByTestId('action-open-lobby').click();
  const code = (await page.getByTestId('join-code').textContent())!.trim();
  const ctx = await browser.newContext();
  const p = await ctx.newPage();
  await logIn(p, player);
  await p.goto(`${site()}/en/games`);
  await p.getByTestId('join-code').fill(code);
  await p.getByTestId('join').click();
  await expect(page.getByTestId('request-Eva')).toBeVisible({ timeout: 20_000 });
  await page.getByTestId('accept-Eva').click();
  await expect(page.getByTestId('member-Eva')).toBeVisible();

  await p.goto(`${site()}/en/games/${gameId}/admin/settings`);
  await expect(p.getByTestId('not-admin')).toBeVisible();
  await ctx.close();
});
