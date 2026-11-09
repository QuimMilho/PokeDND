// The brain of the fake mod: it takes one message from the API and says what the mod would send back.
// It does not know about sockets or the clock, so every handler can be unit tested (see tools/test/fake-mod.test.mjs).
import crypto from 'node:crypto';
import {
  PROTOCOL_VERSION, ack, envelope, errorReply, parse, checkVersion, stateHash,
} from './protocol.mjs';

export const MOD_VERSION = 'fake-mod 0.1.0';
export const MINECRAFT_VERSION = '1.21.1';
/** Commands the fake mod only records and acknowledges: it has no screen to show them on. */
const RECORD_ONLY = [
  'encounter.start', 'battle.start', 'battle.update', 'battle.end', 'roll.request', 'roll.update',
  'camera.shot', 'camera.jump', 'hud.state', 'time.set', 'weather.set', 'link.confirmed',
];

const fakeUuid = (id) => {
  const h = crypto.createHash('sha1').update(`fake-mod:${id}`).digest('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`;
};
const isPoint = (p) => p && typeof p === 'object' && ['x', 'y', 'z'].every((k) => typeof p[k] === 'number');
const idsOf = (d) => (Array.isArray(d.trainerIds) ? d.trainerIds : d.trainerId ? [d.trainerId] : []);

/**
 * @param {object}   deps
 * @param {(message: object) => void} deps.send        sends one envelope to the API
 * @param {(fn: Function, ms: number) => any} [deps.schedule]  setTimeout; tests pass a manual one
 * @param {(handle: any) => void} [deps.cancel]         clearTimeout
 * @param {(text: string) => void} [deps.show]          where "on-screen" lines go (join code, spawns)
 * @param {object}   [deps.options]
 * @param {number}   [deps.options.walkDelayMs=1500]    time from a walk command to the arrival
 * @param {number}   [deps.options.skinDelayMs=200]     time from skin.apply to skin.applied
 * @param {string[]} [deps.options.blockedTrainers]     trainers whose walks end in walk.blocked
 * @param {string[]} [deps.options.failSkins]           trainers whose skin cannot be applied
 * @param {object}   [deps.options.configFiles]         the world files the fake server "has": path to text
 */
export class FakeMod {
  constructor({ send, schedule = setTimeout, cancel = clearTimeout, show = () => {}, options = {} }) {
    this.sendRaw = send;
    this.schedule = schedule;
    this.cancel = cancel;
    this.show = show;
    this.options = { walkDelayMs: 1500, skinDelayMs: 200, blockedTrainers: [], failSkins: [], configFiles: {}, ...options };
    this.seen = new Set();
    this.timers = new Map(); // trainerId -> handle
    this.state = {
      ready: false, serverId: null, protocolVersion: null, game: null, gameState: null,
      lobby: { open: false, code: null }, players: {}, config: { version: 0, files: { ...this.options.configFiles } },
      skins: {}, recorded: [],
    };
    this.onReady = () => {};
  }

  // ---------- sending ----------
  send(type, data, { ref = null, gameId = null } = {}) { this.sendRaw(envelope(type, data, { ref, gameId })); }
  reply(to, extra) { this.sendRaw(ack(to, extra)); }
  fail(to, code, message) { this.sendRaw(errorReply(to, code, message)); }

  /** The `hello` the mod sends right after connecting (18.5). */
  hello() {
    this.send('hello', {
      modVersion: MOD_VERSION, minecraftVersion: MINECRAFT_VERSION, protocolVersion: PROTOCOL_VERSION,
      features: ['fakePlayers', 'walk', 'skins', 'configSync'], fake: true, stateHash: this.stateHash(),
      players: Object.keys(this.state.players), // the fake players it has, so that the API spawns only the missing ones after a reconnect (14.7)
    });
  }

  /** What the mod knows, as plain data (for scenarios and for the state hash). */
  snapshot() {
    const { ready, serverId, protocolVersion, game, gameState, lobby, players, config, skins } = this.state;
    return { ready, serverId, protocolVersion, game, gameState, lobby, players, config: { version: config.version, files: Object.keys(config.files).sort() }, skins };
  }
  stateHash() {
    const { lobby, gameState, players, config } = this.state;
    return stateHash({ lobby, gameState, players, config });
  }
  manifest() {
    return { version: this.state.config.version, files: Object.fromEntries(Object.entries(this.state.config.files).map(([p, t]) => [p, stateHash(t)])) };
  }

  // ---------- receiving ----------
  /** Handles one message from the API (text or object). Throws ProtocolMismatchError for an unknown protocol version. */
  receive(raw) {
    let m;
    try { m = parse(raw); } catch (e) { this.fail(null, e.code ?? 'BAD_MESSAGE', e.message); return; }
    checkVersion(m.v, `message "${m.type}"`);
    if (this.seen.has(m.id)) return; // receivers ignore ids they have seen (18.3)
    this.seen.add(m.id);
    if (m.type === 'ack' || m.type === 'error' || m.type === 'pong') { this.state.recorded.push(m); return; }
    const handler = this.handlers[m.type];
    if (handler) return handler.call(this, m, m.data ?? {});
    if (RECORD_ONLY.includes(m.type)) { this.state.recorded.push(m); this.show(`[${m.type}] (recorded, nothing to show)`); return this.reply(m); }
    this.fail(m, 'UNKNOWN_TYPE', `The fake mod does not know the message type "${m.type}".`);
  }

  get handlers() {
    return {
      'hello.ack': this.onHelloAck, 'lobby.open': this.onLobbyOpen, 'lobby.close': this.onLobbyClose,
      'game.state': this.onGameState, 'trainer.spawn': this.onSpawn, 'trainer.update': this.onUpdate,
      'trainer.remove': this.onRemove, 'skin.apply': this.onSkin, 'party.walk': this.onWalk,
      'party.stop': this.onStop, 'party.teleport': this.onTeleport, 'config.patch': this.onConfigPatch,
      'config.missing': this.onConfigMissing, 'resync.request': this.onResync,
    };
  }

  onHelloAck(m, d) {
    checkVersion(d.protocolVersion ?? m.v, 'hello.ack');
    Object.assign(this.state, { ready: true, serverId: d.serverId ?? null, protocolVersion: d.protocolVersion ?? m.v, game: d.game ?? null });
    if (d.game?.state) this.state.gameState = d.game.state;
    if (d.game?.lobbyCode) this.state.lobby = { open: true, code: d.game.lobbyCode };
    this.show(`Connected as server ${this.state.serverId ?? '?'} (protocol ${this.state.protocolVersion}).`);
    this.send('config.manifest', this.manifest()); // answer config sync: say which world files we have
    this.onReady(this.state);
  }

  onLobbyOpen(m, d) {
    if (!d.code) return this.fail(m, 'INVALID_PAYLOAD', 'lobby.open needs a "code".');
    this.state.lobby = { open: true, code: d.code };
    this.show(`JOIN CODE: ${d.code}`);
    this.reply(m);
  }
  onLobbyClose(m) {
    this.state.lobby = { open: false, code: null };
    this.show('Join code hidden.');
    this.reply(m);
  }
  onGameState(m, d) {
    this.state.gameState = d.state ?? null;
    this.show(`Game is ${d.state}.`);
    this.reply(m);
  }

  onSpawn(m, d) {
    if (!d.trainerId) return this.fail(m, 'INVALID_PAYLOAD', 'trainer.spawn needs a "trainerId".');
    const position = isPoint(d.position) ? d.position : { x: 0, y: 64, z: 0 };
    this.state.players[d.trainerId] = { id: d.trainerId, name: d.name ?? d.trainerId, position, look: d.look ?? null, uuid: fakeUuid(d.trainerId) };
    this.show(`Spawned fake player ${d.name ?? d.trainerId} at ${position.x} ${position.y} ${position.z}.`);
    this.reply(m);
    this.send('trainer.spawned', { trainerId: d.trainerId, uuid: fakeUuid(d.trainerId), position }, { ref: m.id, gameId: m.gameId });
  }
  onUpdate(m, d) {
    const p = this.state.players[d.trainerId];
    if (!p) return this.fail(m, 'UNKNOWN_TRAINER', `No fake player for trainer ${d.trainerId}.`);
    const { trainerId, ...changes } = d;
    Object.assign(p, changes);
    this.reply(m);
  }
  onRemove(m, d) {
    if (!this.state.players[d.trainerId]) return this.fail(m, 'UNKNOWN_TRAINER', `No fake player for trainer ${d.trainerId}.`);
    this.stopTimer(d.trainerId);
    delete this.state.players[d.trainerId];
    delete this.state.skins[d.trainerId];
    this.show(`Removed fake player ${d.trainerId}.`);
    this.reply(m);
  }

  onSkin(m, d) {
    const p = this.state.players[d.trainerId];
    if (!p) return this.fail(m, 'UNKNOWN_TRAINER', `No fake player for trainer ${d.trainerId}.`);
    const source = d.url ?? d.username ?? null;
    if (!source) return this.fail(m, 'INVALID_PAYLOAD', 'skin.apply needs a "url" or a "username".');
    this.reply(m);
    this.schedule(() => {
      if (!this.state.players[d.trainerId]) return;
      if (this.options.failSkins.includes(d.trainerId)) {
        this.send('error', { code: 'SKIN_FAILED', message: `The skin of ${d.trainerId} could not be applied (fake mod option).` }, { ref: m.id, gameId: m.gameId });
        return;
      }
      this.state.skins[d.trainerId] = source;
      this.send('skin.applied', { trainerId: d.trainerId, source }, { ref: m.id, gameId: m.gameId });
    }, this.options.skinDelayMs);
  }

  onWalk(m, d) {
    const ids = idsOf(d);
    if (!ids.length) return this.fail(m, 'INVALID_PAYLOAD', 'party.walk needs "trainerIds".');
    const unknown = ids.filter((id) => !this.state.players[id]);
    if (unknown.length) return this.fail(m, 'UNKNOWN_TRAINER', `No fake player for trainer ${unknown.join(', ')}.`);
    this.reply(m);
    for (const id of ids) {
      const p = this.state.players[id];
      this.stopTimer(id);
      const from = { ...p.position };
      const to = isPoint(d.to) ? d.to : from;
      const meta = { trainerId: id, ...(d.walkId ? { walkId: d.walkId } : {}), ...(d.nodeId ? { nodeId: d.nodeId } : {}) };
      if (this.options.blockedTrainers.includes(id)) {
        this.schedule(() => this.send('walk.blocked', { ...meta, position: from, reason: 'blocked (fake mod option)' }, { ref: m.id, gameId: m.gameId }), 0);
        continue;
      }
      const half = { x: (from.x + to.x) / 2, y: (from.y + to.y) / 2, z: (from.z + to.z) / 2 };
      const delay = this.options.walkDelayMs;
      this.show(`${p.name} starts walking.`);
      const t1 = this.schedule(() => {
        p.position = half;
        this.send('walk.progress', { ...meta, fraction: 0.5, position: half }, { ref: m.id, gameId: m.gameId });
        const t2 = this.schedule(() => {
          this.timers.delete(id);
          p.position = { ...to };
          this.show(`${p.name} arrived.`);
          this.send('walk.arrived', { ...meta, position: p.position }, { ref: m.id, gameId: m.gameId });
        }, delay - Math.floor(delay / 2));
        this.timers.set(id, t2);
      }, Math.floor(delay / 2));
      this.timers.set(id, t1);
    }
  }
  onStop(m, d) {
    const ids = idsOf(d);
    for (const id of ids.length ? ids : Object.keys(this.state.players)) this.stopTimer(id);
    this.reply(m);
  }
  onTeleport(m, d) {
    const ids = idsOf(d);
    if (!ids.length || !isPoint(d.to)) return this.fail(m, 'INVALID_PAYLOAD', 'party.teleport needs "trainerIds" and a point "to".');
    const unknown = ids.filter((id) => !this.state.players[id]);
    if (unknown.length) return this.fail(m, 'UNKNOWN_TRAINER', `No fake player for trainer ${unknown.join(', ')}.`);
    this.reply(m);
    for (const id of ids) {
      this.stopTimer(id);
      this.state.players[id].position = { ...d.to };
      this.send('walk.arrived', { trainerId: id, position: { ...d.to }, teleported: true }, { ref: m.id, gameId: m.gameId });
    }
  }

  onConfigPatch(m, d) {
    if (!d.files || typeof d.files !== 'object') return this.fail(m, 'INVALID_PAYLOAD', 'config.patch needs "files".');
    for (const [path, text] of Object.entries(d.files)) {
      if (text === null) delete this.state.config.files[path]; else this.state.config.files[path] = String(text);
    }
    this.state.config.version = d.version ?? this.state.config.version + 1;
    this.reply(m);
    this.send('config.applied', { version: this.state.config.version, files: Object.keys(d.files).sort(), hash: this.stateHash() }, { ref: m.id, gameId: m.gameId });
  }
  onConfigMissing(m) {
    this.reply(m);
    this.send('config.manifest', this.manifest(), { ref: m.id, gameId: m.gameId });
  }
  onResync(m) {
    this.reply(m);
    this.hello();
    this.send('config.manifest', this.manifest(), { ref: m.id, gameId: m.gameId });
    for (const p of Object.values(this.state.players)) this.send('trainer.spawned', { trainerId: p.id, uuid: p.uuid, position: p.position }, { ref: m.id, gameId: m.gameId });
  }

  stopTimer(id) {
    const t = this.timers.get(id);
    if (t !== undefined) this.cancel(t);
    this.timers.delete(id);
  }
  /** Cancels every pending walk and skin (when the connection ends). */
  dispose() { for (const id of [...this.timers.keys()]) this.stopTimer(id); }
}
