// Phase 27 and the M1 gate: a person registers, logs in, exports Generation 1 with backs, and gets a print-ready PDF.
// Real browser, real API, new empty database. The PDF is then read the way a printer's customer would: its pages are counted,
// drawn, and every QR code on the backs is decoded from the picture and compared with the export's manifest.
import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { PASSWORD, accessToken, newEmail, site } from './helpers';
// @ts-expect-error plain JavaScript helper without type declarations
import { cardsPerSheet, openPdf, readManifest } from './pdf-tools.mjs';

test('M1: register, log in, export Generation 1 with backs, download a valid PDF whose QR codes decode', async ({ page, request }, testInfo) => {
  const email = newEmail('m1');

  // --- register, then log out and log in again with the new account
  await page.goto(`${site()}/en/register`);
  await page.getByTestId('displayName').fill('Printer');
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill(PASSWORD);
  await page.getByTestId('submit').click();
  await expect(page.getByTestId('greeting')).toContainText('Hello, Printer');
  await page.getByTestId('logout').click();
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill(PASSWORD);
  await page.getByTestId('submit').click();
  await expect(page.getByTestId('greeting')).toBeVisible();

  // --- the export screen: Generation 1, with the backs
  await page.getByRole('link', { name: 'Export cards' }).click();
  await expect(page).toHaveURL(/\/en\/cards\/export$/);
  await expect(page.getByTestId('start')).toBeDisabled(); // nothing chosen yet
  await page.getByTestId('generation-1').click();
  await page.getByTestId('backs').click();
  await expect(page.getByTestId('summary')).toContainText(/\d+ cards/);
  const summary = (await page.getByTestId('summary').textContent()) ?? '';
  const chosen = Number(/(\d+) cards/.exec(summary)![1]);
  expect(chosen).toBeGreaterThanOrEqual(151); // 151 species, plus their alternate forms

  await page.getByTestId('start').click();
  await expect(page.getByTestId('job')).toBeVisible();
  await expect(page.getByTestId('job-done')).toBeVisible({ timeout: 4 * 60_000 });

  // --- download through the button
  const pagesText = (await page.getByTestId('job-done').textContent()) ?? '';
  const pagesShown = Number(/(\d+) pages/.exec(pagesText)![1]);
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByTestId('download').click()]);
  expect(download.suggestedFilename()).toMatch(/^pokednd-cards-[0-9a-f]{8}\.pdf$/);
  const path = testInfo.outputPath('generation-1.pdf');
  await download.saveAs(path);
  const bytes = readFileSync(path);

  // --- the file is a PDF with the pages we expect
  expect(bytes.subarray(0, 5).toString('latin1')).toBe('%PDF-');
  const pdf = await openPdf(bytes);
  expect(pdf.pages).toBe(pagesShown);

  // the manifest says what is on each page; get it from the API with the same account
  const token = await accessToken(request, email);
  const jobs = await (await request.get(`${site()}/api/v1/cards/exports`, { headers: { Authorization: `Bearer ${token}` } })).json();
  expect(jobs).toHaveLength(1);
  const job = jobs[0];
  expect(job.state).toBe('done');
  expect(job.total).toBe(chosen);
  expect(job.options.backs).toBe(true);
  const manifestAnswer = await request.get(`${site()}${job.manifestUrl}`, { headers: { Authorization: `Bearer ${token}` } });
  expect(manifestAnswer.status()).toBe(200);
  const rows = readManifest(await manifestAnswer.text());
  expect(rows).toHaveLength(2 * chosen); // a front row and a back row for every card

  // Expected pages: A4, 63 x 88 mm, 3 mm bleed, 2 mm gap give this many cards per sheet; each sheet has a front and a back page.
  const perSheet = cardsPerSheet({ paperMm: [210, 297], cardMm: [63, 88], bleedMm: 3, gapMm: 2 });
  expect(perSheet).toBe(9);
  const sheets = Math.ceil(chosen / perSheet);
  expect(pdf.pages).toBe(2 * sheets);
  expect(Math.max(...rows.map((r: { page: string }) => Number(r.page)))).toBe(pdf.pages);

  // Every card is on a front page and on a back page; the cards of Generation 1 are in it.
  const keys = new Set(rows.map((r: { card_key: string }) => r.card_key));
  for (const key of ['0001_bulbasaur', '0025_pikachu', '0150_mewtwo', '0151_mew']) expect(keys.has(key)).toBe(true);
  expect(keys.size).toBe(chosen);
  expect(rows.filter((r: { side: string }) => r.side === 'front')).toHaveLength(chosen);
  expect(rows.filter((r: { side: string }) => r.side === 'back')).toHaveLength(chosen);

  // --- every QR code on every back page decodes, and is the code the manifest lists for that page
  const decodedAll = new Set<string>();
  for (let pageNumber = 1; pageNumber <= pdf.pages; pageNumber++) {
    const onPage = rows.filter((r: { page: string }) => Number(r.page) === pageNumber);
    const side = onPage[0].side;
    if (side === 'front') {
      // the fronts carry no QR code; two of them are enough to check (the first and the last)
      if (pageNumber === 1 || pageNumber === pdf.pages - 1) {
        expect(await pdf.qrCodes(pageNumber, 100), `front page ${pageNumber} has no QR codes`).toEqual([]);
      }
    } else {
      const decoded: string[] = await pdf.qrCodes(pageNumber, 100);
      expect([...decoded].sort(), `QR codes on back page ${pageNumber}`).toEqual(onPage.map((r: { qr_id: string }) => r.qr_id).sort());
      decoded.forEach((d) => decodedAll.add(d));
    }
  }
  expect(decodedAll.size).toBe(chosen);
  for (const id of decodedAll) expect(id).toMatch(/^PKC1-[0-9A-HJKMNP-TV-Z]{16}$/);

  // --- the earlier-exports page lists it and offers the download again
  await page.getByRole('link', { name: 'Earlier exports' }).click();
  await expect(page.getByTestId('jobs-list')).toContainText(`${chosen} cards, ${pdf.pages} pages`);
});

test('the export screen explains an empty selection and refuses too many copies before asking the server', async ({ page, request }) => {
  const email = newEmail('m1b');
  await request.post(`${site()}/api/v1/auth/register`, { data: { email, password: PASSWORD, displayName: 'Anna', locale: 'en' } });
  await page.goto(`${site()}/en/login`);
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill(PASSWORD);
  await page.getByTestId('submit').click();
  await page.getByRole('link', { name: 'Export cards' }).click();

  // by hand: search a card and add it
  await page.getByTestId('hand-search').fill('pika');
  await page.getByTestId('add-0025_pikachu').click();
  await expect(page.getByTestId('hand-chosen')).toContainText('Pikachu');
  await expect(page.getByTestId('summary')).toContainText('1 card');

  // 1 card x 1,600 copies is more than one export may have
  await page.getByTestId('copies').fill('1600');
  await expect(page.getByRole('alert')).toContainText('more than the 1500 cards');
  await expect(page.getByTestId('start')).toBeDisabled();
  await page.getByTestId('copies').fill('2');
  await expect(page.getByTestId('start')).toBeEnabled();
});
