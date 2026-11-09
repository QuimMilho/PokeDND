// Tests for the fake mod (phase 39). Run: node --test "tools/test/*.test.mjs"
// Part 1: every message handler on its own, with a manual clock.
// Part 2: the real connection against a small stub server (hello, spawn / walk / arrive, config sync, scenario mode).
// Part 3: an incompatible protocol version is refused with a clear message.
import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import fs from 'node:fs';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { FakeMod } from '../fake-mod/mod.mjs';
import { FakeModClient, runScenario, getPath, matches } from '../fake-mod/client.mjs';
import { PROTOCOL_VERSION, ProtocolMismatchError, envelope, parse, ulid, stateHash } from '../fake-mod/protocol.mjs';
import { startStub } from './lib/ws-stub.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const cli = path.join(here, '../fake-mod/fake-mod.mjs');

// ---------- Part 1: handlers ----------
function manualMod(options = {}) {
  const sent = [];
  const shown = [];
  const timers = [];
  let now = 0;
  const mod = new FakeMod({
    send: (m) => sent.push(m),
    show: (t) => shown.push(t),
    schedule: (fn, ms) => { const t = { fn, at: now + ms, live: true }; timers.push(t); return t; },
    cancel: (t) => { t.live = false; },
    options: { walkDelayMs: 100, skinDelayMs: 10, ...options },
  });
  const advance = (ms) => {
    const end = now + ms;
    for (;;) {
      const due = timers.filter((t) => t.live && t.at <= end).sort((a, b) => a.at - b.at)[0];
      if (!due) break;
      now = Math.max(now, due.at);
      due.live = false; due.fn();
    }
    now = end;
  };
  const types = () => sent.map((m) => m.type);
  const last = (type) => [...sent].reverse().find((m) => m.type === type);
  return { mod, sent, shown, advance, types, last };
}
const command = (type, data = {}) => envelope(type, data, { gameId: 'g1' });
const hello = (mod, data = {}) => mod.receive(envelope('hello.ack', { serverId: 'srv', protocolVersion: PROTOCOL_VERSION, ...data }));
const spawnAna = (mod, position = { x: 0, y: 64, z: 0 }) => mod.receive(command('trainer.spawn', { trainerId: 'ana', name: 'Ana', position }));

test('envelope: ULID ids, version 1, ref and ts', () => {
  const a = envelope('x', {}, { ref: 'r1', gameId: 'g' });
  assert.equal(a.v, 1);
  assert.match(a.id, /^[0-9A-HJKMNP-TV-Z]{26}$/);
  assert.equal(a.ref, 'r1');
  assert.equal(a.gameId, 'g');
  assert.match(a.ts, /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/);
  assert.notEqual(ulid(), ulid());
  assert.throws(() => parse('not json'), /not JSON/);
  assert.throws(() => parse('{"id":"a","v":1}'), /no "type"/);
});

test('hello: the mod says its versions, features and a state hash', () => {
  const { mod, sent } = manualMod();
  mod.hello();
  assert.equal(sent[0].type, 'hello');
  assert.equal(sent[0].data.protocolVersion, PROTOCOL_VERSION);
  assert.ok(sent[0].data.modVersion && sent[0].data.minecraftVersion);
  assert.ok(sent[0].data.features.includes('fakePlayers'));
  assert.match(sent[0].data.stateHash, /^[0-9a-f]{16}$/);
  assert.deepEqual(sent[0].data.players, []);
});

test('hello lists the fake players the mod has, so the API spawns only the missing ones after a reconnect', () => {
  const { mod, sent } = manualMod();
  spawnAna(mod);
  sent.length = 0;
  mod.hello();
  assert.deepEqual(sent[0].data.players, ['ana']);
});

test('hello.ack: becomes ready, remembers the server and sends the config manifest', () => {
  const { mod, types, last } = manualMod({ configFiles: { 'regions/a.json': '{}' } });
  let ready = false;
  mod.onReady = () => { ready = true; };
  hello(mod, { game: { id: 'g1', state: 'lobby', lobbyCode: 'XK7Q2M4P' } });
  assert.equal(ready, true);
  assert.equal(mod.state.serverId, 'srv');
  assert.equal(mod.state.gameState, 'lobby');
  assert.deepEqual(mod.state.lobby, { open: true, code: 'XK7Q2M4P' });
  assert.deepEqual(types(), ['config.manifest']);
  assert.deepEqual(Object.keys(last('config.manifest').data.files), ['regions/a.json']);
});

test('lobby.open shows the join code, lobby.close hides it', () => {
  const { mod, shown, types, last } = manualMod();
  mod.receive(command('lobby.open', { code: 'ABCD2345' }));
  assert.deepEqual(mod.state.lobby, { open: true, code: 'ABCD2345' });
  assert.ok(shown.some((s) => s.includes('ABCD2345')));
  assert.equal(last('ack').ref !== null, true);
  mod.receive(command('lobby.close'));
  assert.deepEqual(mod.state.lobby, { open: false, code: null });
  assert.deepEqual(types(), ['ack', 'ack']);
});

test('lobby.open without a code is an error that answers the message', () => {
  const { mod, last } = manualMod();
  const m = command('lobby.open', {});
  mod.receive(m);
  assert.equal(last('error').ref, m.id);
  assert.equal(last('error').data.code, 'INVALID_PAYLOAD');
});

test('game.state is remembered', () => {
  const { mod } = manualMod();
  mod.receive(command('game.state', { state: 'running' }));
  assert.equal(mod.state.gameState, 'running');
});

test('trainer.spawn: adds a fake player and confirms with trainer.spawned (same uuid every time)', () => {
  const { mod, types, last } = manualMod();
  const m = command('trainer.spawn', { trainerId: 'ana', name: 'Ana', position: { x: 1, y: 70, z: 2 } });
  mod.receive(m);
  assert.deepEqual(types(), ['ack', 'trainer.spawned']);
  const spawned = last('trainer.spawned');
  assert.equal(spawned.ref, m.id);
  assert.equal(spawned.gameId, 'g1');
  assert.deepEqual(spawned.data.position, { x: 1, y: 70, z: 2 });
  assert.match(spawned.data.uuid, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-a[0-9a-f]{3}-[0-9a-f]{12}$/);
  const other = manualMod();
  other.mod.receive(command('trainer.spawn', { trainerId: 'ana', name: 'Ana' }));
  assert.equal(other.last('trainer.spawned').data.uuid, spawned.data.uuid);
});

test('trainer.spawn without trainerId is an error', () => {
  const { mod, last } = manualMod();
  mod.receive(command('trainer.spawn', { name: 'Nobody' }));
  assert.equal(last('error').data.code, 'INVALID_PAYLOAD');
  assert.deepEqual(mod.state.players, {});
});

test('trainer.update changes a player, trainer.remove removes it, unknown ones are errors', () => {
  const { mod, last } = manualMod();
  mod.receive(command('trainer.update', { trainerId: 'ghost' }));
  assert.equal(last('error').data.code, 'UNKNOWN_TRAINER');
  spawnAna(mod);
  mod.receive(command('trainer.update', { trainerId: 'ana', name: 'Ana B.' }));
  assert.equal(mod.state.players.ana.name, 'Ana B.');
  mod.receive(command('trainer.remove', { trainerId: 'ana' }));
  assert.deepEqual(mod.state.players, {});
  mod.receive(command('trainer.remove', { trainerId: 'ana' }));
  assert.equal(last('error').data.code, 'UNKNOWN_TRAINER');
});

test('skin.apply: confirms with skin.applied after a delay; can be made to fail', () => {
  const { mod, types, last, advance } = manualMod({ failSkins: ['bob'] });
  spawnAna(mod);
  mod.receive(command('trainer.spawn', { trainerId: 'bob', name: 'Bob' }));
  mod.receive(command('skin.apply', { trainerId: 'ana', url: 'https://example.test/ana.png' }));
  assert.ok(!types().includes('skin.applied'));
  advance(10);
  assert.deepEqual(last('skin.applied').data, { trainerId: 'ana', source: 'https://example.test/ana.png' });
  assert.equal(mod.state.skins.ana, 'https://example.test/ana.png');
  mod.receive(command('skin.apply', { trainerId: 'bob', username: 'Notch' }));
  advance(10);
  assert.equal(last('error').data.code, 'SKIN_FAILED');
  assert.equal(mod.state.skins.bob, undefined);
  mod.receive(command('skin.apply', { trainerId: 'ana' }));
  assert.equal(last('error').data.code, 'INVALID_PAYLOAD');
});

test('party.walk: progress halfway, arrival after the delay, position updated', () => {
  const { mod, types, last, advance } = manualMod();
  spawnAna(mod, { x: 0, y: 64, z: 0 });
  const m = command('party.walk', { trainerIds: ['ana'], to: { x: 10, y: 64, z: 20 }, walkId: 'w1', nodeId: 'route-1' });
  mod.receive(m);
  assert.equal(types().filter((t) => t === 'walk.arrived').length, 0);
  advance(49);
  assert.equal(types().includes('walk.progress'), false);
  advance(1);
  assert.deepEqual(last('walk.progress').data, { trainerId: 'ana', walkId: 'w1', nodeId: 'route-1', fraction: 0.5, position: { x: 5, y: 64, z: 10 } });
  advance(49);
  assert.equal(types().includes('walk.arrived'), false);
  advance(1);
  const arrived = last('walk.arrived');
  assert.equal(arrived.ref, m.id);
  assert.deepEqual(arrived.data.position, { x: 10, y: 64, z: 20 });
  assert.deepEqual(mod.state.players.ana.position, { x: 10, y: 64, z: 20 });
});

test('party.walk: several trainers arrive; an unknown trainer is an error and nobody walks', () => {
  const { mod, sent, last, advance } = manualMod();
  spawnAna(mod);
  mod.receive(command('trainer.spawn', { trainerId: 'bob', name: 'Bob' }));
  mod.receive(command('party.walk', { trainerIds: ['ana', 'bob'], to: { x: 1, y: 64, z: 1 } }));
  advance(100);
  assert.deepEqual(sent.filter((m) => m.type === 'walk.arrived').map((m) => m.data.trainerId).sort(), ['ana', 'bob']);
  mod.receive(command('party.walk', { trainerIds: ['ana', 'ghost'], to: { x: 5, y: 64, z: 5 } }));
  assert.equal(last('error').data.code, 'UNKNOWN_TRAINER');
  advance(500);
  assert.deepEqual(mod.state.players.ana.position, { x: 1, y: 64, z: 1 });
  mod.receive(command('party.walk', {}));
  assert.equal(last('error').data.code, 'INVALID_PAYLOAD');
});

test('party.walk: a blocked trainer reports walk.blocked and does not move', () => {
  const { mod, last, types, advance } = manualMod({ blockedTrainers: ['ana'] });
  spawnAna(mod);
  mod.receive(command('party.walk', { trainerIds: ['ana'], to: { x: 9, y: 64, z: 9 } }));
  advance(0);
  assert.equal(last('walk.blocked').data.trainerId, 'ana');
  advance(1000);
  assert.equal(types().includes('walk.arrived'), false);
  assert.deepEqual(mod.state.players.ana.position, { x: 0, y: 64, z: 0 });
});

test('party.stop cancels a walk; a new walk replaces an old one', () => {
  const { mod, types, last, advance } = manualMod();
  spawnAna(mod);
  mod.receive(command('party.walk', { trainerIds: ['ana'], to: { x: 10, y: 64, z: 0 } }));
  advance(20);
  mod.receive(command('party.stop', { trainerIds: ['ana'] }));
  advance(1000);
  assert.equal(types().includes('walk.arrived'), false);
  mod.receive(command('party.walk', { trainerIds: ['ana'], to: { x: 10, y: 64, z: 0 } }));
  mod.receive(command('party.walk', { trainerIds: ['ana'], to: { x: 0, y: 64, z: 7 } }));
  advance(1000);
  assert.equal(types().filter((t) => t === 'walk.arrived').length, 1);
  assert.deepEqual(last('walk.arrived').data.position, { x: 0, y: 64, z: 7 });
});

test('party.teleport moves at once and reports the arrival', () => {
  const { mod, last } = manualMod();
  spawnAna(mod);
  mod.receive(command('party.teleport', { trainerIds: ['ana'], to: { x: 3, y: 65, z: 4 } }));
  assert.deepEqual(mod.state.players.ana.position, { x: 3, y: 65, z: 4 });
  assert.equal(last('walk.arrived').data.teleported, true);
  mod.receive(command('party.teleport', { trainerIds: ['ana'] }));
  assert.equal(last('error').data.code, 'INVALID_PAYLOAD');
});

test('config.patch applies files and answers config.applied; config.missing sends the manifest', () => {
  const { mod, last } = manualMod({ configFiles: { 'old.json': '{}' } });
  mod.receive(command('config.patch', { version: 3, files: { 'regions/a.json': '{"a":1}', 'old.json': null } }));
  assert.equal(mod.state.config.version, 3);
  assert.deepEqual(Object.keys(mod.state.config.files), ['regions/a.json']);
  assert.deepEqual(last('config.applied').data.files, ['old.json', 'regions/a.json']);
  assert.match(last('config.applied').data.hash, /^[0-9a-f]{16}$/);
  mod.receive(command('config.missing', { files: ['x'] }));
  const manifest = last('config.manifest');
  assert.equal(manifest.data.version, 3);
  assert.deepEqual(Object.keys(manifest.data.files), ['regions/a.json']);
  mod.receive(command('config.patch', {}));
  assert.equal(last('error').data.code, 'INVALID_PAYLOAD');
});

test('resync.request sends everything again', () => {
  const { mod, sent } = manualMod();
  spawnAna(mod);
  sent.length = 0;
  mod.receive(command('resync.request'));
  assert.deepEqual(sent.map((m) => m.type), ['ack', 'hello', 'config.manifest', 'trainer.spawned']);
});

test('commands without a screen are recorded and acknowledged; unknown types are errors', () => {
  const { mod, last } = manualMod();
  for (const type of ['encounter.start', 'battle.start', 'roll.request', 'camera.shot', 'hud.state', 'time.set', 'weather.set', 'link.confirmed']) {
    mod.receive(command(type, {}));
    assert.equal(last('ack').type, 'ack', type);
  }
  assert.equal(mod.state.recorded.length, 8);
  const m = command('made.up');
  mod.receive(m);
  assert.equal(last('error').data.code, 'UNKNOWN_TYPE');
  assert.equal(last('error').ref, m.id);
});

test('a message id seen before is ignored; bad messages get an error', () => {
  const { mod, sent } = manualMod();
  const m = command('lobby.open', { code: 'ABCD2345' });
  mod.receive(m);
  mod.receive(m);
  assert.equal(sent.length, 1);
  mod.receive('{{{');
  assert.equal(sent.at(-1).type, 'error');
  assert.equal(sent.at(-1).data.code, 'BAD_MESSAGE');
});

test('a message with another protocol version is refused', () => {
  const { mod } = manualMod();
  assert.throws(() => mod.receive({ ...command('lobby.open', { code: 'X' }), v: 2 }), ProtocolMismatchError);
  assert.throws(() => hello(mod, { protocolVersion: 7 }), /protocol version 7/);
});

test('helpers: dotted paths, partial matches, stable hashes', () => {
  assert.equal(getPath({ a: { b: [1, 2] } }, 'a.b.1'), 2);
  assert.equal(getPath({ a: 1 }, 'a.b.c'), undefined);
  assert.ok(matches({ a: 1, b: { c: 2, d: 3 } }, { b: { c: 2 } }));
  assert.ok(!matches({ a: 1 }, { a: 2 }));
  assert.equal(stateHash({ a: 1, b: 2 }), stateHash({ b: 2, a: 1 }));
});

// ---------- Part 2: against a stub server ----------
const quick = { walkDelayMs: 40, skinDelayMs: 5 };

test('stub server: the token goes in the Authorization header; a wrong token is refused', async () => {
  const stub = await startStub({ token: 'secret' });
  try {
    const bad = new FakeModClient({ url: stub.url, token: 'wrong', options: quick });
    await assert.rejects(bad.start({ timeoutMs: 2000 }), /Could not connect/);
    assert.ok(stub.rejected >= 1);
    const good = new FakeModClient({ url: stub.url, token: 'secret', options: quick });
    await good.start({ timeoutMs: 2000 });
    good.close();
  } finally { await stub.close(); }
});

test('stub server: hello, hello.ack and the config manifest', async () => {
  const stub = await startStub();
  const client = new FakeModClient({ url: stub.url, token: stub.token, options: { ...quick, configFiles: { 'paths/p.json': '{}' } } });
  try {
    const state = await client.start({ timeoutMs: 2000 });
    assert.equal(state.ready, true);
    assert.equal(state.serverId, 'srv-test');
    const hello = await stub.waitFor('hello');
    assert.equal(hello.v, 1);
    assert.equal(hello.data.protocolVersion, PROTOCOL_VERSION);
    assert.equal(hello.data.fake, true);
    const manifest = await stub.waitFor('config.manifest');
    assert.deepEqual(Object.keys(manifest.data.files), ['paths/p.json']);
  } finally { client.close(); await stub.close(); }
});

test('stub server: lobby, spawn, skin, walk and arrival', async () => {
  const stub = await startStub();
  const shown = [];
  const client = new FakeModClient({ url: stub.url, token: stub.token, options: quick, print: (t) => shown.push(t) });
  try {
    await client.start({ timeoutMs: 2000 });
    const open = stub.command('lobby.open', { code: 'ZZ99AA77' });
    await stub.waitFor('ack', { match: (m) => m.ref === open.id });
    assert.ok(shown.some((t) => t.includes('JOIN CODE: ZZ99AA77')));

    const spawn = stub.command('trainer.spawn', { trainerId: 'ana', name: 'Ana', position: { x: 0, y: 64, z: 0 } });
    const spawned = await stub.waitFor('trainer.spawned');
    assert.equal(spawned.ref, spawn.id);

    stub.command('skin.apply', { trainerId: 'ana', username: 'Notch' });
    assert.equal((await stub.waitFor('skin.applied')).data.source, 'Notch');

    const walk = stub.command('party.walk', { trainerIds: ['ana'], to: { x: 8, y: 64, z: 8 } });
    const arrived = await stub.waitFor('walk.arrived');
    assert.equal(arrived.ref, walk.id);
    assert.deepEqual(arrived.data.position, { x: 8, y: 64, z: 8 });
    assert.ok(stub.received.findIndex((m) => m.type === 'walk.progress') < stub.received.findIndex((m) => m.type === 'walk.arrived'));
  } finally { client.close(); await stub.close(); }
});

test('stub server: config.patch and resync', async () => {
  const stub = await startStub();
  const client = new FakeModClient({ url: stub.url, token: stub.token, options: quick });
  try {
    await client.start({ timeoutMs: 2000 });
    stub.command('config.patch', { version: 2, files: { 'npcs/n.json': '{}' } });
    assert.deepEqual((await stub.waitFor('config.applied')).data.files, ['npcs/n.json']);
    stub.command('resync.request');
    await stub.waitFor('hello', { skip: 1 });
    const manifest = await stub.waitFor('config.manifest', { skip: 1 });
    assert.deepEqual(Object.keys(manifest.data.files), ['npcs/n.json']);
  } finally { client.close(); await stub.close(); }
});

test('scenario mode: waits, sends, checks the state and ends', async () => {
  const stub = await startStub();
  const lines = [];
  try {
    const run = runScenario({
      options: quick,
      steps: [
        { waitFor: 'lobby.open', data: { code: 'AB12CD34' } },
        { expect: { 'lobby.open': true, 'lobby.code': 'AB12CD34' } },
        { send: { type: 'player.joined', data: { uuid: 'u-1', name: 'Ana' } } },
        { waitFor: 'trainer.spawn' },
        { waitFor: 'trainer.spawned', dir: 'out' },
        { waitFor: 'party.walk' },
        { waitFor: 'walk.arrived', dir: 'out', timeoutMs: 2000 },
        { expect: { 'players.ana.position.x': 4 } },
        { print: 'done' },
        { close: true },
      ],
    }, { url: stub.url, token: stub.token, print: (t) => lines.push(t) });
    await stub.waitFor('hello');
    stub.command('lobby.open', { code: 'AB12CD34' });
    assert.equal((await stub.waitFor('player.joined')).data.name, 'Ana');
    stub.command('trainer.spawn', { trainerId: 'ana', name: 'Ana', position: { x: 0, y: 64, z: 0 } });
    stub.command('party.walk', { trainerIds: ['ana'], to: { x: 4, y: 64, z: 0 } });
    const result = await run;
    assert.equal(result.ok, true);
    assert.equal(result.steps, 10);
    assert.ok(lines.includes('done'));
  } finally { await stub.close(); }
});

test('scenario mode: a step that never happens fails with its number', async () => {
  const stub = await startStub();
  try {
    await assert.rejects(
      runScenario({ options: quick, steps: [{ waitFor: 'lobby.close', timeoutMs: 100 }] }, { url: stub.url, token: stub.token }),
      /Scenario failed at step 1 .*lobby\.close.*did not come/s,
    );
    await assert.rejects(
      runScenario({ options: quick, steps: [{ expect: { gameState: 'running' } }] }, { url: stub.url, token: stub.token }),
      /Scenario failed at step 1.*gameState is null, expected "running"/s,
    );
  } finally { await stub.close(); }
});

// ---------- Part 3: incompatible protocol ----------
function runCli(args, env = {}) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [cli, ...args], { env: { ...process.env, ...env }, stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '', err = '';
    child.stdout.on('data', (d) => { out += d; });
    child.stderr.on('data', (d) => { err += d; });
    child.on('close', (code) => resolve({ code, out, err }));
  });
}

test('incompatible protocol in hello.ack: the client rejects with a clear message', async () => {
  const stub = await startStub({ protocolVersion: 99 });
  const client = new FakeModClient({ url: stub.url, token: stub.token, options: quick });
  try {
    await assert.rejects(client.start({ timeoutMs: 2000 }), (e) => {
      assert.ok(e instanceof ProtocolMismatchError);
      assert.match(e.message, /protocol version 99/);
      assert.match(e.message, /this fake mod speaks version 1/);
      assert.match(e.message, /Update the fake mod/);
      return true;
    });
  } finally { await stub.close(); }
});

test('incompatible protocol: the command line prints the message and exits with 3', async () => {
  for (const options of [{ protocolVersion: 99 }, { envelopeVersion: 2 }]) {
    const stub = await startStub(options);
    try {
      const { code, err } = await runCli(['--url', stub.url, '--token', stub.token]);
      assert.equal(code, 3, err);
      assert.match(err, /protocol version (99|2)/);
      assert.match(err, /this fake mod speaks version 1/);
    } finally { await stub.close(); }
  }
});

test('command line: scenario file, missing arguments, wrong token', async () => {
  const stub = await startStub();
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'fake-mod-'));
  try {
    const file = path.join(dir, 'scenario.json');
    fs.writeFileSync(file, JSON.stringify({ options: quick, steps: [{ waitFor: 'lobby.open' }, { expect: { 'lobby.code': 'QQ22WW33' } }] }));
    const child = runCli(['--scenario', file, '--url', stub.url, '--token', stub.token, '--quiet']);
    await stub.waitFor('hello');
    await stub.waitFor('config.manifest');
    stub.command('lobby.open', { code: 'QQ22WW33' });
    const done = await child;
    assert.equal(done.code, 0, done.err);
    assert.match(done.out, /JOIN CODE: QQ22WW33/);
    assert.match(done.out, /Scenario finished: 2 steps passed/);

    const none = await runCli([], { FAKE_MOD_URL: '', FAKE_MOD_TOKEN: '' });
    assert.equal(none.code, 2);
    assert.match(none.err, /--url and --token/);
    const bad = await runCli(['--url', stub.url, '--token', 'wrong']);
    assert.equal(bad.code, 1);
    assert.match(bad.err, /Could not connect/);
    const unknown = await runCli(['--nope']);
    assert.equal(unknown.code, 2);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); await stub.close(); }
});
