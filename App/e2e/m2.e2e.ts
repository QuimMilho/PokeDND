// Phase 41, milestone M2: the whole flow from a clean start, with the fake mod standing in for Minecraft (spec chapter 2, steps 1 to 4).
//
//   site admin registers a server and the "mod" connects -> the admin creates a game and opens the lobby -> three players join with the code
//   and are accepted -> each makes a trainer -> the admin starts the game.
//
// What the fake mod receives is checked at every step: the join code on its screen, one spawn command per trainer, the game state.
import { APIRequestContext, expect, test } from '@playwright/test';
import { connectFakeMod } from './fake-mod';
import { gameWithOpenLobby, makeTrainer, skinPng } from './flow';
import { ADMIN_EMAIL, accessToken, createAccount, createServer, logIn, newEmail, site } from './helpers';

/** The status the API shows for a Minecraft server: "online" while a mod is connected with its token. */
async function serverStatus(request: APIRequestContext, serverId: string): Promise<string> {
  const admin = await accessToken(request, ADMIN_EMAIL);
  const answer = await request.get(`${site()}/api/v1/servers/${serverId}/status`, { headers: { Authorization: `Bearer ${admin}` } });
  expect(answer.status()).toBe(200);
  return (await answer.json()).status;
}

test('M2: an admin opens a lobby, three players join and make trainers, and the fake mod gets the spawn commands', async ({ page, request, browser }) => {
  const tag = Date.now().toString(36).slice(-5).toUpperCase();
  const people = [
    { name: 'Ana', trainer: `Ash_${tag}`, classId: 'ace_trainer', skills: ['pokemon_handling', 'insight'], starter: 'charmander', skin: true, language: 'en' },
    { name: 'Bruno', trainer: `Bob_${tag}`, classId: 'ranger', skills: ['survival', 'stealth'], starter: 'squirtle', skin: false, language: 'en' },
    { name: 'Carla', trainer: `Cia_${tag}`, classId: 'medic', skills: ['medicine', 'insight'], starter: 'bulbasaur', skin: false, language: 'pt' },
  ].map((p) => ({ ...p, email: newEmail(p.name.toLowerCase()) }));

  // --- step 1: the server is registered and the mod connects with its token
  const server = await createServer(request);
  expect(await serverStatus(request, server.id)).toBe('offline');
  const mod = await connectFakeMod(server.token);
  try {
    expect(mod.snapshot().ready).toBe(true);
    expect(mod.snapshot().serverId).toBe(server.id);
    await expect.poll(() => serverStatus(request, server.id)).toBe('online');
    expect(mod.messages('config.manifest', 'out')).toHaveLength(1); // config sync: the mod told the API which world files it has

    // --- step 2: the admin creates the game and opens the lobby; the TV (the fake mod's screen) shows the join code
    for (const p of people) await createAccount(request, p.email, p.name);
    const gameId = await gameWithOpenLobby(page, server.name, `M2 ${tag}`, 'builtin:casual');
    await page.getByTestId('action-open-lobby').click();
    await expect(page).toHaveURL(new RegExp(`/en/games/${gameId}/lobby$`));
    const code = (await page.getByTestId('join-code').textContent())!.trim();
    expect(code).toMatch(/^[A-Z0-9]{6}$/);

    await expect.poll(() => mod.messages('lobby.open').map((m) => m.data['code'])).toEqual([code]);
    expect(mod.messages('lobby.open')[0].gameId).toBe(gameId);
    expect(mod.snapshot().lobby).toEqual({ open: true, code });
    expect(mod.screen).toContain(`JOIN CODE: ${code}`);
    expect(mod.messages('game.state').at(-1)?.data['state']).toBe('lobby');

    // --- step 3: the three players join with the code on their phones, and the admin accepts each
    const contexts = [];
    const phones = [];
    for (const p of people) {
      const context = await browser.newContext();
      contexts.push(context);
      const phone = await context.newPage();
      phones.push(phone);
      await logIn(phone, p.email, p.language);
      await phone.goto(`${site()}/${p.language}/games`);
      await phone.getByTestId('join-code').fill(code);
      await phone.getByTestId('join').click();
      await expect(phone.getByTestId('joined')).toBeVisible();
    }
    for (const p of people) {
      await expect(page.getByTestId(`request-${p.name}`)).toBeVisible({ timeout: 20_000 });
      await page.getByTestId(`accept-${p.name}`).click();
      await expect(page.getByTestId(`member-${p.name}`)).toBeVisible();
    }
    expect(mod.messages('trainer.spawn')).toHaveLength(0); // nobody has a trainer yet

    // --- each player makes a trainer; the fake player of each one is spawned as soon as the trainer exists
    for (const [i, p] of people.entries()) {
      await makeTrainer(phones[i], gameId, { trainer: p.trainer, classId: p.classId, skills: p.skills, starter: p.starter, skin: p.skin ? skinPng() : undefined, language: p.language });
      await expect.poll(() => mod.messages('trainer.spawn').map((m) => m.data['name'])).toContain(p.trainer);
    }

    // --- what the fake mod received: a spawn command for each of the three trainers, and nothing for anybody else
    const admin = await accessToken(request, ADMIN_EMAIL);
    const trainers: { id: string; name: string; userId: string }[] = await (
      await request.get(`${site()}/api/v1/games/${gameId}/trainers`, { headers: { Authorization: `Bearer ${admin}` } })
    ).json();
    expect(trainers.map((t) => t.name).sort()).toEqual(people.map((p) => p.trainer).sort());

    const spawns = mod.messages('trainer.spawn');
    expect(spawns).toHaveLength(3);
    expect(spawns.map((m) => m.data['trainerId']).sort()).toEqual(trainers.map((t) => t.id).sort());
    for (const p of people) {
      const trainer = trainers.find((t) => t.name === p.trainer)!;
      const spawn = spawns.find((m) => m.data['trainerId'] === trainer.id)!;
      expect(spawn.gameId).toBe(gameId);
      expect(spawn.data['name']).toBe(p.trainer);
      expect(spawn.data['userId']).toBe(trainer.userId);
      // a skin that was uploaded reaches the mod as its own command (the wizard makes the trainer first, then sends the skin)
      if (p.skin) {
        await expect.poll(() => mod.messages('skin.apply').filter((m) => m.data['trainerId'] === trainer.id).length).toBeGreaterThan(0);
        const skin = mod.messages('skin.apply').find((m) => m.data['trainerId'] === trainer.id)!;
        expect(skin.data['sha256']).toMatch(/^[0-9a-f]{64}$/); // the mod can fetch the uploaded skin and check it
        expect(skin.data['model']).toBe('slim');
        expect(skin.data['url']).toContain(trainer.id);
      } else {
        expect(mod.messages('skin.apply').filter((m) => m.data['trainerId'] === trainer.id)).toEqual([]);
      }
      expect(mod.screen.some((line) => line.includes(`Spawned fake player ${p.trainer}`))).toBe(true);
    }
    // the fake mod confirmed every spawn, and its three fake players stand in the world
    await expect.poll(() => mod.messages('trainer.spawned', 'out').length).toBe(3);
    expect(Object.values(mod.snapshot().players).map((player) => player.name).sort()).toEqual(people.map((p) => p.trainer).sort());

    // --- step 4: the admin starts the game; the mod hears that the lobby is closed
    await page.getByTestId('to-game').click();
    await page.getByTestId('action-start').click();
    await expect(page.getByTestId('game-state')).toHaveText('Running');
    await expect.poll(() => mod.messages('lobby.close').length).toBe(1);
    await expect.poll(() => mod.messages('game.state').at(-1)?.data['state']).toBe('running');
    expect(mod.snapshot().lobby.open).toBe(false);
    expect(mod.snapshot().gameState).toBe('running');
    expect(mod.messages('trainer.spawn')).toHaveLength(3); // starting the game spawns nobody twice

    // the whole way the mod never had to complain, and the connection stayed up
    expect(mod.messages('error', 'out')).toEqual([]);
    expect(mod.messages('error')).toEqual([]);
    expect(await serverStatus(request, server.id)).toBe('online');

    for (const context of contexts) await context.close();
  } finally {
    mod.close();
  }
  // the server goes offline when the mod leaves
  await expect.poll(() => serverStatus(request, server.id), { timeout: 20_000 }).toBe('offline');
});
