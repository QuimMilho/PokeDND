// Phase 38: three players join one game and each makes a trainer with the wizard (one of them in Portuguese, one with a skin).
import { APIRequestContext, Page, expect, test } from '@playwright/test';
import { createCanvas } from '@napi-rs/canvas';
import { ADMIN_EMAIL, accessToken, createAccount, createServer, logIn, newEmail, site } from './helpers';

/** A real 64x64 PNG skin (the API reads it again and keeps only the pixels). */
function skinPng(): Buffer {
  const canvas = createCanvas(64, 64);
  const g = canvas.getContext('2d');
  g.fillStyle = '#3366cc';
  g.fillRect(8, 8, 48, 48);
  g.fillStyle = '#ffcc00';
  g.fillRect(24, 24, 16, 16);
  return canvas.toBuffer('image/png');
}

interface Player {
  email: string;
  name: string;
  id: string;
}

/** Makes a game with an open lobby through the API, and returns its id and join code. */
async function openLobby(request: APIRequestContext): Promise<{ gameId: string; code: string; admin: string }> {
  const server = await createServer(request);
  const admin = await accessToken(request, ADMIN_EMAIL);
  const headers = { Authorization: `Bearer ${admin}` };
  const game = await (await request.post(`${site()}/api/v1/games`, { data: { serverId: server.id, name: `Trainers ${Date.now()}` }, headers })).json();
  const opened = await request.post(`${site()}/api/v1/games/${game.id}/lobby/open`, { headers });
  expect(opened.status()).toBe(200);
  return { gameId: game.id, code: (await opened.json()).joinCode, admin };
}

/** A new account that joins the game and is accepted by the admin. */
async function joinedPlayer(request: APIRequestContext, game: { gameId: string; code: string; admin: string }, name: string): Promise<Player> {
  const email = newEmail(name.toLowerCase());
  await createAccount(request, email, name);
  const token = await accessToken(request, email);
  const me = await (await request.get(`${site()}/api/v1/me`, { headers: { Authorization: `Bearer ${token}` } })).json();
  const joined = await request.post(`${site()}/api/v1/games/join`, { data: { code: game.code, role: 'player' }, headers: { Authorization: `Bearer ${token}` } });
  expect(joined.status()).toBe(200);
  const admin = { Authorization: `Bearer ${game.admin}` };
  const requests = await (await request.get(`${site()}/api/v1/games/${game.gameId}/requests`, { headers: admin })).json();
  const mine = requests.find((r: { userId: string }) => r.userId === me.id);
  const accepted = await request.post(`${site()}/api/v1/games/${game.gameId}/requests/${mine.id}/accept`, { headers: admin });
  expect(accepted.status()).toBe(200);
  return { email, name, id: me.id };
}

/** Walks the wizard: class, scores (point buy: the cheapest spread that costs 27), two skills, the starter, the name and maybe a skin. */
async function makeTrainer(page: Page, gameId: string, wanted: { trainer: string; classId: string; skills: string[]; starter: string; skin?: Buffer; language?: string }): Promise<void> {
  const language = wanted.language ?? 'en';
  await page.goto(`${site()}/${language}/games/${gameId}`);
  await page.getByTestId('to-trainer').click();
  await expect(page).toHaveURL(new RegExp(`/${language}/games/${gameId}/trainer/create$`));

  await page.getByTestId(`class-${wanted.classId}`).click();
  await page.getByTestId('next').click();

  // 15 14 13 11 10 9 costs 27 points
  for (const [ability, clicks] of [['str', 7], ['dex', 6], ['con', 5], ['int', 3], ['wis', 2], ['cha', 1]] as const) {
    for (let i = 0; i < clicks; i++) await page.getByTestId(`inc-${ability}`).click();
  }
  await expect(page.getByTestId('points-left')).toContainText('0');
  await page.getByTestId('next').click();

  for (const skill of wanted.skills) await page.getByTestId(`skill-${skill}`).check();
  await page.getByTestId('next').click();

  await page.getByTestId(`starter-${wanted.starter}`).click();
  await page.getByTestId('next').click();

  await page.getByTestId('name').fill(wanted.trainer);
  if (wanted.skin) {
    await page.getByTestId('skin-mode-upload').locator('input').check();
    await page.getByTestId('skin-file').setInputFiles({ name: 'skin.png', mimeType: 'image/png', buffer: wanted.skin });
    await expect(page.getByTestId('skin-chosen')).toHaveText('skin.png');
    await page.getByTestId('skin-slim').locator('input').check();
  }
  await page.getByTestId('next').click();

  await expect(page.getByTestId('sum-name')).toHaveText(wanted.trainer);
  await page.getByTestId('submit').click();
  await expect(page).toHaveURL(new RegExp(`/${language}/games/${gameId}/trainer$`));
}

test('three players make three trainers in one game', async ({ request, browser }) => {
  const game = await openLobby(request);
  const ana = await joinedPlayer(request, game, 'Ana');
  const bruno = await joinedPlayer(request, game, 'Bruno');
  const carla = await joinedPlayer(request, game, 'Carla');
  const tag = Date.now().toString(36).slice(-5).toUpperCase();
  const names = { ana: `Ash_${tag}`, bruno: `Bob_${tag}`, carla: `Cia_${tag}` };

  // Ana: an Ace Trainer with a Charmander and an uploaded slim skin
  const anaContext = await browser.newContext();
  const anaPage = await anaContext.newPage();
  await logIn(anaPage, ana.email);
  await makeTrainer(anaPage, game.gameId, { trainer: names.ana, classId: 'ace_trainer', skills: ['pokemon_handling', 'insight'], starter: 'charmander', skin: skinPng() });
  await expect(anaPage.getByTestId('trainer-name')).toHaveText(names.ana);
  await expect(anaPage.getByTestId('trainer-class')).toContainText('Ace Trainer');
  await expect(anaPage.getByTestId('hp')).toHaveText('9 / 9'); // d8 at its maximum, CON 13 is +1
  await expect(anaPage.getByTestId('ac')).toHaveText('12');
  await expect(anaPage.getByTestId('starter')).toContainText('Charmander');
  await expect(anaPage.getByTestId('skin-image')).toBeVisible();
  await expect(anaPage.getByTestId('skin-info')).toHaveText('Uploaded, slim arms');
  await expect(anaPage.getByTestId('features')).toContainText('Commanding Presence');

  // Bruno: a Ranger with a Squirtle
  const brunoContext = await browser.newContext();
  const brunoPage = await brunoContext.newPage();
  await logIn(brunoPage, bruno.email);
  await makeTrainer(brunoPage, game.gameId, { trainer: names.bruno, classId: 'ranger', skills: ['survival', 'stealth'], starter: 'squirtle' });
  await expect(brunoPage.getByTestId('trainer-class')).toContainText('Ranger');
  await expect(brunoPage.getByTestId('hp')).toHaveText('11 / 11'); // d10 + 1
  await expect(brunoPage.getByTestId('bag')).toContainText('8 × poke_ball'); // the Ranger's 3 extra Poké Balls
  await expect(brunoPage.getByTestId('no-skin')).toBeVisible();

  // Carla: the same wizard in Portuguese, a Medic with a Bulbasaur; her name is taken by nobody else
  const carlaContext = await browser.newContext();
  const carlaPage = await carlaContext.newPage();
  await logIn(carlaPage, carla.email, 'pt');
  await carlaPage.goto(`${site()}/pt/games/${game.gameId}`);
  await carlaPage.getByTestId('to-trainer').click();
  await expect(carlaPage.getByTestId('wizard-title')).toHaveText('Crie o seu treinador');
  await carlaPage.getByTestId('next').click();
  await expect(carlaPage.getByTestId('step-problems')).toContainText('Escolha uma classe.');
  await makeTrainer(carlaPage, game.gameId, { trainer: names.carla, classId: 'medic', skills: ['medicine', 'insight'], starter: 'bulbasaur', language: 'pt' });
  await expect(carlaPage.getByTestId('trainer-class')).toContainText('Medic');
  await expect(carlaPage.getByTestId('page-title')).toHaveText('O seu treinador');
  await expect(carlaPage.getByTestId('bag')).toContainText('2 × antidote');

  // everybody sees the other two on their page
  await anaPage.reload();
  await expect(anaPage.getByTestId(`other-${names.bruno}`)).toContainText('Ranger');
  await expect(anaPage.getByTestId(`other-${names.carla}`)).toContainText('Medic');

  // a second trainer for the same person is not possible: the wizard goes to the trainer page
  await anaPage.goto(`${site()}/en/games/${game.gameId}/trainer/create`);
  await expect(anaPage).toHaveURL(new RegExp(`/en/games/${game.gameId}/trainer$`));

  // the admin's list has the three
  const list = await (await request.get(`${site()}/api/v1/games/${game.gameId}/trainers`, { headers: { Authorization: `Bearer ${game.admin}` } })).json();
  expect(list.map((t: { name: string }) => t.name).sort()).toEqual([names.ana, names.bruno, names.carla].sort());
  expect(list.map((t: { userId: string }) => t.userId).sort()).toEqual([ana.id, bruno.id, carla.id].sort());

  await anaContext.close();
  await brunoContext.close();
  await carlaContext.close();
});

test('a name that another trainer has is refused, and a trainer can be changed before the game starts', async ({ request, browser }) => {
  const game = await openLobby(request);
  const dora = await joinedPlayer(request, game, 'Dora');
  const eli = await joinedPlayer(request, game, 'Eli');
  const name = `Dup_${Date.now().toString(36).slice(-5).toUpperCase()}`;

  const doraPage = await (await browser.newContext()).newPage();
  await logIn(doraPage, dora.email);
  await makeTrainer(doraPage, game.gameId, { trainer: name, classId: 'hiker', skills: ['athletics', 'survival'], starter: 'charmander' });

  // Eli tries the same name in lower case: the wizard says so before anything is sent
  const eliPage = await (await browser.newContext()).newPage();
  await logIn(eliPage, eli.email);
  await eliPage.goto(`${site()}/en/games/${game.gameId}/trainer/create`);
  await eliPage.getByTestId('class-ace_trainer').click();
  await eliPage.getByTestId('next').click();
  await eliPage.getByTestId('next').click();
  await eliPage.getByTestId('skill-pokemon_handling').check();
  await eliPage.getByTestId('skill-insight').check();
  await eliPage.getByTestId('next').click();
  await eliPage.getByTestId('starter-squirtle').click();
  await eliPage.getByTestId('next').click();
  await eliPage.getByTestId('name').fill(name.toLowerCase());
  await eliPage.getByTestId('next').click();
  await expect(eliPage.getByTestId('step-problems')).toContainText('is already called');

  // Dora changes her class while the lobby is open
  await doraPage.getByTestId('edit').click();
  await expect(doraPage).toHaveURL(new RegExp(`/en/games/${game.gameId}/trainer/edit$`));
  await expect(doraPage.getByTestId('wizard-title')).toHaveText('Change your trainer');
  await doraPage.getByTestId('class-ace_trainer').click();
  await doraPage.getByTestId('next').click(); // scores: chosen again
  await doraPage.getByTestId('next').click();
  await expect(doraPage.getByTestId('skills-left')).toContainText('Ace Trainer');
  await doraPage.getByTestId('skill-pokemon_handling').check();
  await doraPage.getByTestId('skill-insight').check();
  await doraPage.getByTestId('next').click();
  await doraPage.getByTestId('next').click();
  await doraPage.getByTestId('next').click();
  await doraPage.getByTestId('submit').click();
  await expect(doraPage).toHaveURL(new RegExp(`/en/games/${game.gameId}/trainer$`));
  await expect(doraPage.getByTestId('trainer-class')).toContainText('Ace Trainer');

  // once the game runs, the player can no longer change it
  const admin = { Authorization: `Bearer ${game.admin}` };
  expect((await request.post(`${site()}/api/v1/games/${game.gameId}/start`, { headers: admin })).status()).toBe(200);
  await doraPage.reload();
  await expect(doraPage.getByTestId('locked')).toBeVisible();
  await expect(doraPage.getByTestId('edit')).toHaveCount(0);
});
