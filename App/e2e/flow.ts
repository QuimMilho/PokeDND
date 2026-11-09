// Steps of the game flow that more than one end-to-end test needs (the games, the trainers and the M2 flow).
import { Page, expect } from '@playwright/test';
import { createCanvas } from '@napi-rs/canvas';
import { logInAsAdmin, site } from './helpers';

/** A real 64x64 PNG skin (the API reads it again and keeps only the pixels). */
export function skinPng(): Buffer {
  const canvas = createCanvas(64, 64);
  const g = canvas.getContext('2d');
  g.fillStyle = '#3366cc';
  g.fillRect(8, 8, 48, 48);
  g.fillStyle = '#ffcc00';
  g.fillRect(24, 24, 16, 16);
  return canvas.toBuffer('image/png');
}

/** The admin makes a game on a registered server (by its name) through the screens. Returns the id of the new game. */
export async function gameWithOpenLobby(page: Page, serverName: string, gameName: string, preset = 'builtin:defaults'): Promise<string> {
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

/** Walks the wizard: class, scores (point buy: the cheapest spread that costs 27), two skills, the starter, the name and maybe a skin. */
export async function makeTrainer(
  page: Page,
  gameId: string,
  wanted: { trainer: string; classId: string; skills: string[]; starter: string; skin?: Buffer; language?: string },
): Promise<void> {
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
