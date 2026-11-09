// Phase 26: the card browser against the real API and the real reference data.
import { expect, test } from '@playwright/test';
import { createAccount, logIn, newEmail, site } from './helpers';

test.beforeEach(async ({ page, request }) => {
  const email = newEmail('browser');
  await createAccount(request, email);
  await logIn(page, email);
  await page.goto(`${site()}/en/cards`);
});

test('Generation 1 has 151 species, shown a page at a time', async ({ page }) => {
  await page.getByTestId('generation-1').click();
  await expect(page.getByTestId('species-bulbasaur')).toBeVisible();
  await expect(page.getByTestId('count')).toHaveText('60 Pokémon shown');

  // "Show more" until the list is complete
  while (await page.getByTestId('more').isVisible()) {
    await page.getByTestId('more').click();
    await page.waitForTimeout(100);
  }
  await expect(page.getByTestId('count')).toHaveText('151 Pokémon shown');
  await expect(page.locator('a[data-testid^="species-"]')).toHaveCount(151);
  await expect(page.getByTestId('species-mew')).toBeVisible();
  await expect(page.getByTestId('species-chikorita')).toHaveCount(0); // Generation 2

  // the art really loaded from the API
  const loaded = await page.getByTestId('species-bulbasaur').locator('img').evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0);
  expect(loaded).toBe(true);
});

test('filters by type and by name, and tells when nothing is found', async ({ page }) => {
  await page.getByTestId('generation-1').click();
  await page.getByTestId('type-fire').click();
  await expect(page.getByTestId('count')).toHaveText('12 Pokémon shown');
  await expect(page.getByTestId('species-charizard')).toBeVisible();

  // either type by default: Bulbasaur is Grass/Poison. Only the first type: Bulbasaur drops out, Zubat (Poison/Flying) stays.
  await page.getByTestId('clear').click();
  await page.getByTestId('generation-1').click();
  await page.getByTestId('type-poison').click();
  await expect(page.getByTestId('species-bulbasaur')).toBeVisible();
  await expect(page.getByTestId('species-zubat')).toBeVisible();
  await page.getByText('Only the first type').click();
  await expect(page.getByTestId('species-bulbasaur')).toHaveCount(0);
  await expect(page.getByTestId('species-zubat')).toBeVisible();

  await page.getByTestId('clear').click();
  await page.getByTestId('search').fill('char');
  const shownNames = () => page.locator('a[data-testid^="species-"] .name').allTextContents();
  // the list is searched a moment after typing stops: wait until only matching names are left
  await expect.poll(async () => (await shownNames()).length > 0 && (await shownNames()).every((n) => n.toLowerCase().includes('char'))).toBe(true);
  expect(await shownNames()).toEqual(expect.arrayContaining(['Charmander', 'Charmeleon', 'Charizard']));

  await page.getByTestId('search').fill('zzzzzz');
  await expect(page.getByTestId('empty')).toContainText('No Pokémon match these filters');
});

test('opens a card preview with the four moves and switches to an alternate form', async ({ page }) => {
  await page.getByTestId('search').fill('marowak');
  await page.getByTestId('species-marowak').click();
  await expect(page).toHaveURL(/\/en\/cards\/marowak$/);

  const card = page.getByTestId('card-preview');
  await expect(page.getByTestId('card-name')).toHaveText('Marowak');
  await expect(page.getByTestId('card-move')).toHaveCount(4);
  await expect(card).toContainText('Ground');
  const picture = card.locator('img');
  await expect(picture).toHaveAttribute('src', '/api/v1/art/0105_marowak/marowak.png');
  expect(await picture.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true);

  const firstMoves = await page.getByTestId('card-move').locator('.move-name').allTextContents();
  await page.getByTestId('form-marowak_alolan').click();
  await expect(page.getByTestId('card-name')).toHaveText('Marowak (Alola)');
  await expect(card).toContainText('Fire');
  await expect(picture).toHaveAttribute('src', '/api/v1/art/0105_marowak/marowak_alolan.png');
  await expect(page.getByTestId('card-move')).toHaveCount(4);
  expect(await page.getByTestId('card-move').locator('.move-name').allTextContents()).not.toEqual(firstMoves);

  // from the card to the export screen with the card already chosen
  await page.getByTestId('export-this').click();
  await expect(page).toHaveURL(/\/en\/cards\/export\?card=0105_marowak_alolan$/);
  await expect(page.getByTestId('summary')).toContainText('1 card');
});

test('works on a phone-sized screen without sideways scrolling', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 700 });
  await page.getByTestId('generation-1').click();
  await expect(page.getByTestId('species-bulbasaur')).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
});
