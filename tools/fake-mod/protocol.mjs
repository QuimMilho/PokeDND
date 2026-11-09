// The mod protocol, as the fake mod speaks it (spec 18.2, 18.3, 18.5).
// No dependencies. The real mod (phase 60) and the API (phase 40) must agree with this file.
import crypto from 'node:crypto';

/** The protocol version in the `v` field of every message and in `hello.ack`. A peer refuses versions it does not know. */
export const PROTOCOL_VERSION = 1;
/** The versions this program understands. */
export const SUPPORTED_VERSIONS = [1];
/** Seconds between two heartbeats (spec 18.2). */
export const HEARTBEAT_SECONDS = 20;

const CROCKFORD = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

/** A new ULID: 10 characters of time, 16 of randomness. Sorted by time, unique. */
export function ulid(now = Date.now()) {
  let time = '';
  let t = now;
  for (let i = 0; i < 10; i++) { time = CROCKFORD[t % 32] + time; t = Math.floor(t / 32); }
  const random = crypto.randomBytes(16);
  let rest = '';
  for (let i = 0; i < 16; i++) rest += CROCKFORD[random[i] % 32];
  return time + rest;
}

/** Builds a message in the envelope of 18.3. `ref` is the id this message answers; `gameId` is left out when there is none. */
export function envelope(type, data = {}, { ref = null, gameId = null, now = new Date() } = {}) {
  const message = { v: PROTOCOL_VERSION, id: ulid(now.getTime()), ref, type };
  if (gameId) message.gameId = gameId;
  message.ts = now.toISOString().replace(/\.\d{3}Z$/, 'Z');
  message.data = data;
  return message;
}

export const ack = (to, extra = {}) => envelope('ack', extra, { ref: to.id, gameId: to.gameId });
export const errorReply = (to, code, message) => envelope('error', { code, message }, { ref: to?.id ?? null, gameId: to?.gameId });

/** A protocol version nobody here understands. Carries a sentence that says what to update. */
export class ProtocolMismatchError extends Error {
  constructor(theirs, where) {
    super(`The API speaks protocol version ${theirs} (${where}); this fake mod speaks version ${PROTOCOL_VERSION}. `
      + 'Update the fake mod (tools/fake-mod) or the API so that both use the same protocol version.');
    this.name = 'ProtocolMismatchError';
    this.theirs = theirs;
  }
}

/** Reads one raw text message. Throws an Error with `code` 'BAD_MESSAGE' when it is not a valid envelope. */
export function parse(raw) {
  let m;
  try { m = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch { throw badMessage('The message is not JSON.'); }
  if (m === null || typeof m !== 'object' || Array.isArray(m)) throw badMessage('The message is not an object.');
  if (typeof m.type !== 'string' || m.type === '') throw badMessage('The message has no "type".');
  if (typeof m.id !== 'string' || m.id === '') throw badMessage('The message has no "id".');
  if (!Number.isInteger(m.v)) throw badMessage('The message has no protocol version "v".');
  if (m.data === undefined) m.data = {};
  return m;
}

function badMessage(text) { return Object.assign(new Error(text), { code: 'BAD_MESSAGE' }); }

/** Throws ProtocolMismatchError when a message has a version this program does not know. */
export function checkVersion(version, where) {
  if (!SUPPORTED_VERSIONS.includes(version)) throw new ProtocolMismatchError(version, where);
}

/** A short hash of any JSON value: the same data always gives the same text (keys are sorted). */
export function stateHash(value) {
  const sorted = (v) => Array.isArray(v) ? v.map(sorted)
    : v && typeof v === 'object' ? Object.fromEntries(Object.keys(v).sort().map((k) => [k, sorted(v[k])])) : v;
  return crypto.createHash('sha256').update(JSON.stringify(sorted(value))).digest('hex').slice(0, 16);
}
