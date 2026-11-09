// The connection of the fake mod (WebSocket client of Node 22 and later, no dependencies) and the scenario runner.
import { FakeMod } from './mod.mjs';
import { HEARTBEAT_SECONDS, ProtocolMismatchError, envelope } from './protocol.mjs';

/** The value at a dotted path ("lobby.code", "players.ana.position.x"), or undefined. */
export function getPath(object, path) {
  return String(path).split('.').reduce((o, key) => (o === null || o === undefined ? undefined : o[key]), object);
}

/** True when every key of `expected` is in `actual` with the same value (objects are compared by their keys, one level at a time). */
export function matches(actual, expected) {
  if (expected === null || typeof expected !== 'object') return Object.is(actual, expected);
  if (actual === null || typeof actual !== 'object') return false;
  return Object.entries(expected).every(([k, v]) => matches(actual[k], v));
}

export class FakeModClient {
  /**
   * @param {object} o
   * @param {string} o.url        for example ws://localhost:8080/ws/mod
   * @param {string} o.token      the server token (sent as "Authorization: Bearer <token>")
   * @param {object} [o.options]  see FakeMod
   * @param {(line: string) => void} [o.print]  where lines for the person go
   * @param {boolean} [o.verbose] print every message too
   * @param {number} [o.heartbeatSeconds]
   * @param {typeof WebSocket} [o.WebSocketImpl]
   */
  constructor({ url, token, options = {}, print = () => {}, verbose = false, heartbeatSeconds = HEARTBEAT_SECONDS, WebSocketImpl = globalThis.WebSocket }) {
    this.url = url;
    this.token = token;
    this.print = print;
    this.verbose = verbose;
    this.heartbeatSeconds = heartbeatSeconds;
    this.WebSocketImpl = WebSocketImpl;
    this.log = []; // { dir: 'in' | 'out', message }
    this.cursor = 0;
    this.waiters = [];
    this.fatal = null;
    this.mod = new FakeMod({ send: (m) => this.write(m), show: (t) => print(t), options });
  }

  /** Connects, sends hello and resolves when the API has answered with hello.ack. Rejects with a clear message otherwise. */
  start({ timeoutMs = 10000 } = {}) {
    if (!this.WebSocketImpl) return Promise.reject(new Error('This Node.js has no WebSocket client. Use Node 22 or later.'));
    return new Promise((resolve, reject) => {
      const fail = (error) => { this.fatal = error; this.close(); reject(error); };
      const timer = setTimeout(() => fail(new Error(`No hello.ack from ${this.url} within ${timeoutMs} ms.`)), timeoutMs);
      this.mod.onReady = () => { clearTimeout(timer); resolve(this.mod.state); };
      let opened = false;
      const ws = new this.WebSocketImpl(this.url, { headers: { Authorization: `Bearer ${this.token}` } });
      this.ws = ws;
      ws.onopen = () => {
        opened = true;
        this.print(`Connected to ${this.url}.`);
        this.heartbeat = setInterval(() => this.write(envelope('ping')), this.heartbeatSeconds * 1000);
        this.heartbeat.unref?.();
        this.mod.hello();
      };
      ws.onmessage = (event) => this.onMessage(String(event.data), fail);
      ws.onerror = () => { if (!opened) { clearTimeout(timer); fail(new Error(`Could not connect to ${this.url}. Is the API running, and is the token a valid server token?`)); } };
      ws.onclose = (event) => {
        clearInterval(this.heartbeat);
        this.mod.dispose();
        this.closed = true;
        this.flushWaiters();
        if (!opened) return;
        if (!this.mod.state.ready) { clearTimeout(timer); fail(new Error(`The API closed the connection before hello.ack (code ${event.code}${event.reason ? `: ${event.reason}` : ''}).`)); }
        else this.print(`Connection closed (code ${event.code}).`);
      };
    });
  }

  onMessage(text, fail) {
    let parsed = null;
    try { parsed = JSON.parse(text); } catch { /* the mod answers with a BAD_MESSAGE error */ }
    this.record('in', parsed ?? { type: '(not json)', data: { text } });
    try { this.mod.receive(text); } catch (error) {
      if (error instanceof ProtocolMismatchError) fail(error); else throw error;
    }
    this.flushWaiters();
  }

  write(message) {
    this.record('out', message);
    if (this.ws && this.ws.readyState === 1) this.ws.send(JSON.stringify(message));
  }

  record(dir, message) {
    this.log.push({ dir, message });
    this.flushWaiters();
    if (this.verbose) this.print(`${dir === 'in' ? '<-' : '->'} ${message.type}${message.data && Object.keys(message.data).length ? ` ${JSON.stringify(message.data)}` : ''}`);
  }

  /** Sends a message the scenario made up (for example player.joined). */
  sendMessage(type, data = {}, extra = {}) { this.write(envelope(type, data, extra)); }

  /**
   * Waits for a message at or after the cursor. `dir` is 'in' (from the API, the default) or 'out' (sent by the fake mod).
   * Resolves with the message and moves the cursor past it, so a scenario reads its messages in order.
   */
  waitFor(type, { dir = 'in', data = null, timeoutMs = 5000 } = {}) {
    return new Promise((resolve, reject) => {
      const waiter = { type, dir, data, resolve, reject };
      waiter.timer = setTimeout(() => {
        this.waiters = this.waiters.filter((w) => w !== waiter);
        reject(new Error(`Waited ${timeoutMs} ms for ${dir === 'in' ? 'a message from the API' : 'a message from the fake mod'} of type "${type}"${data ? ` with ${JSON.stringify(data)}` : ''}; it did not come.`));
      }, timeoutMs);
      this.waiters.push(waiter);
      this.flushWaiters();
    });
  }

  flushWaiters() {
    for (const waiter of [...this.waiters]) {
      const index = this.log.findIndex((entry, i) => i >= this.cursor && entry.dir === waiter.dir && entry.message.type === waiter.type && (!waiter.data || matches(entry.message.data, waiter.data)));
      if (index < 0) { if (this.closed) { clearTimeout(waiter.timer); this.waiters = this.waiters.filter((w) => w !== waiter); waiter.reject(new Error(`The connection closed while waiting for "${waiter.type}".`)); } continue; }
      this.cursor = index + 1;
      clearTimeout(waiter.timer);
      this.waiters = this.waiters.filter((w) => w !== waiter);
      waiter.resolve(this.log[index].message);
    }
  }

  close() {
    clearInterval(this.heartbeat);
    this.mod.dispose();
    try { this.ws?.close(1000, 'bye'); } catch { /* already closed */ }
  }

  /** Resolves when the socket is closed. */
  closedPromise() {
    return new Promise((resolve) => { if (this.closed) resolve(); else { const previous = this.ws.onclose; this.ws.onclose = (e) => { previous(e); resolve(); }; } });
  }
}

/**
 * Runs a scenario (a JSON file's content). Steps:
 *   { "waitFor": "lobby.open", "dir": "in", "data": { ... }, "timeoutMs": 5000 }   wait for a message (data is a partial match)
 *   { "send": { "type": "player.joined", "data": { ... } } }                      send a message of our own
 *   { "sleep": 100 }                                                                wait, in milliseconds
 *   { "expect": { "lobby.code": "ABC123", "players.ana.position.x": 10 } }          check the fake mod's state (dotted paths)
 *   { "print": "text" }                                                             write a line
 *   { "close": true }                                                               close the connection
 * Resolves with { ok: true, steps }. Rejects with an Error that names the failing step.
 */
export async function runScenario(scenario, { url, token, print = () => {}, verbose = false, WebSocketImpl, heartbeatSeconds } = {}) {
  const client = new FakeModClient({
    url: url ?? scenario.url, token: token ?? scenario.token, print, verbose, WebSocketImpl, heartbeatSeconds,
    options: scenario.options ?? {},
  });
  await client.start({ timeoutMs: scenario.helloTimeoutMs ?? 10000 });
  let n = 0;
  try {
    for (const step of scenario.steps ?? []) {
      n++;
      const label = `step ${n} (${JSON.stringify(step).slice(0, 120)})`;
      try {
        if (step.waitFor) await client.waitFor(step.waitFor, { dir: step.dir ?? 'in', data: step.data ?? null, timeoutMs: step.timeoutMs ?? 5000 });
        else if (step.send) client.sendMessage(step.send.type, step.send.data ?? {}, { gameId: step.send.gameId ?? null, ref: step.send.ref ?? null });
        else if (step.sleep !== undefined) await new Promise((r) => setTimeout(r, step.sleep));
        else if (step.expect) {
          const snapshot = client.mod.snapshot();
          for (const [path, want] of Object.entries(step.expect)) {
            const got = getPath(snapshot, path);
            if (!matches(got, want)) throw new Error(`${path} is ${JSON.stringify(got)}, expected ${JSON.stringify(want)}.`);
          }
        } else if (step.print !== undefined) print(String(step.print));
        else if (step.close) { client.close(); await client.closedPromise(); }
        else throw new Error('Unknown step. Use waitFor, send, sleep, expect, print or close.');
      } catch (error) {
        throw new Error(`Scenario failed at ${label}: ${error.message}`);
      }
    }
  } finally {
    client.close();
  }
  return { ok: true, steps: n, log: client.log, state: client.mod.snapshot() };
}
