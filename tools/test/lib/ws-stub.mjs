// A small WebSocket server for tests, written with node:http and node:crypto (Node has a WebSocket client, but no server).
// It plays the API's side of /ws/mod: it checks the token, answers hello with hello.ack and lets a test send commands.
import http from 'node:http';
import crypto from 'node:crypto';
import { PROTOCOL_VERSION, envelope } from '../../fake-mod/protocol.mjs';

const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

function frame(opcode, payload = Buffer.alloc(0)) {
  const n = payload.length;
  const head = n < 126 ? Buffer.from([0x80 | opcode, n])
    : n < 65536 ? Buffer.from([0x80 | opcode, 126, n >> 8, n & 255])
      : Buffer.concat([Buffer.from([0x80 | opcode, 127]), (() => { const b = Buffer.alloc(8); b.writeBigUInt64BE(BigInt(n)); return b; })()]);
  return Buffer.concat([head, payload]);
}

/** Cuts complete frames out of `buffer`. Returns { frames, rest }. Client frames are masked. */
function readFrames(buffer) {
  const frames = [];
  let offset = 0;
  for (;;) {
    if (buffer.length - offset < 2) break;
    const b0 = buffer[offset], b1 = buffer[offset + 1];
    let len = b1 & 127, pos = offset + 2;
    if (len === 126) { if (buffer.length < pos + 2) break; len = buffer.readUInt16BE(pos); pos += 2; }
    else if (len === 127) { if (buffer.length < pos + 8) break; len = Number(buffer.readBigUInt64BE(pos)); pos += 8; }
    const masked = (b1 & 128) !== 0;
    if (buffer.length < pos + (masked ? 4 : 0) + len) break;
    let payload;
    if (masked) {
      const mask = buffer.subarray(pos, pos + 4);
      payload = Buffer.from(buffer.subarray(pos + 4, pos + 4 + len));
      for (let i = 0; i < len; i++) payload[i] ^= mask[i & 3];
      pos += 4;
    } else payload = buffer.subarray(pos, pos + len);
    frames.push({ opcode: b0 & 15, payload });
    offset = pos + len;
  }
  return { frames, rest: buffer.subarray(offset) };
}

/**
 * @param {object} [o]
 * @param {string} [o.token='test-token']       the server token it accepts
 * @param {number} [o.protocolVersion]          the version it puts in hello.ack (default: the current one)
 * @param {number} [o.envelopeVersion]          the `v` of its messages (default: the current one)
 * @param {object} [o.game]                     put in hello.ack
 * @param {boolean} [o.autoAck=true]            answer hello with hello.ack on its own
 */
export async function startStub({ token = 'test-token', protocolVersion = PROTOCOL_VERSION, envelopeVersion = PROTOCOL_VERSION, game = null, autoAck = true } = {}) {
  const received = []; // every message the fake mod sent, parsed
  const waiters = [];
  const sockets = new Set();
  let current = null;
  let rejected = 0;
  const server = http.createServer((req, res) => { res.writeHead(426); res.end('Upgrade required'); });
  server.on('upgrade', (req, socket) => {
    if (req.headers.authorization !== `Bearer ${token}`) { rejected++; socket.end('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n'); return; }
    const accept = crypto.createHash('sha1').update(req.headers['sec-websocket-key'] + GUID).digest('base64');
    socket.write(`HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept}\r\n\r\n`);
    sockets.add(socket);
    current = socket;
    let pending = Buffer.alloc(0);
    socket.on('data', (chunk) => {
      const out = readFrames(Buffer.concat([pending, chunk]));
      pending = out.rest;
      for (const f of out.frames) {
        if (f.opcode === 8) { socket.write(frame(8)); socket.end(); }
        else if (f.opcode === 9) socket.write(frame(10, f.payload));
        else if (f.opcode === 1) {
          const message = JSON.parse(f.payload.toString('utf8'));
          received.push(message);
          if (message.type === 'hello' && autoAck) {
            stub.send(envelope('hello.ack', { serverId: 'srv-test', protocolVersion, ...(game ? { game } : {}) }, { ref: message.id }), { v: envelopeVersion });
          }
          for (const w of [...waiters]) w.check();
        }
      }
    });
    socket.on('close', () => { sockets.delete(socket); if (current === socket) current = null; });
    socket.on('error', () => {});
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();

  const stub = {
    url: `ws://127.0.0.1:${port}/ws/mod`,
    token,
    received,
    get rejected() { return rejected; },
    /** Sends one message to the connected fake mod. `override` replaces fields of the envelope (for example { v: 2 }). */
    send(message, override = {}) { current?.write(frame(1, Buffer.from(JSON.stringify({ ...message, ...override })))); },
    command(type, data = {}, gameId = 'game-1') { const m = envelope(type, data, { gameId }); stub.send(m); return m; },
    /** Resolves with the first message of `type` the fake mod has sent (optionally one more than `skip` earlier ones). */
    waitFor(type, { timeoutMs = 3000, match = () => true, skip = 0 } = {}) {
      return new Promise((resolve, reject) => {
        const w = {
          check() {
            const hits = received.filter((m) => m.type === type && match(m));
            if (hits.length > skip) { clearTimeout(timer); waiters.splice(waiters.indexOf(w), 1); resolve(hits[skip]); }
          },
        };
        const timer = setTimeout(() => { waiters.splice(waiters.indexOf(w), 1); reject(new Error(`The stub never received "${type}". Got: ${received.map((m) => m.type).join(', ')}`)); }, timeoutMs);
        waiters.push(w);
        w.check();
      });
    },
    async close() {
      for (const s of sockets) s.destroy();
      await new Promise((resolve) => server.close(resolve));
    },
  };
  return stub;
}
