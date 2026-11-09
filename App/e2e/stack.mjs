// Starts the real thing for the end-to-end tests (chapter 20.4): a fresh, empty database, the built API, and a small web server that serves the
// built app and passes /api/* to the API - the same job Caddy does in production (deploy/Caddyfile). Everything starts from an empty
// database, so each run is a "clean database" run.
//
//   node e2e/stack.mjs            starts the stack and prints the address; stop it with Ctrl+C (handy for trying the app by hand)
//
// Needs: a PostgreSQL server (default postgres://test:test@127.0.0.1:55432/postgres, or set POKEDND_E2E_PG; see API/README.md), Java,
// the API built (`./gradlew quarkusBuild` in API/), the data bundle (build/bundle), the resources (resources/) and the app built
// (`npm run build`).
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { generateKeyPairSync, randomBytes } from 'node:crypto';
import { mkdtempSync, existsSync, createWriteStream, rmSync, mkdirSync } from 'node:fs';
import { readFile, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import net from 'node:net';
import pg from 'pg';

const appDir = resolve(fileURLToPath(new URL('..', import.meta.url)));
const repoDir = resolve(appDir, '..');
const apiDir = join(repoDir, 'API');
const webRoot = join(appDir, 'dist', 'pokednd-app', 'browser');

const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.webmanifest': 'application/manifest+json',
  '.json': 'application/json',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.svg': 'image/svg+xml',
};

function freePort() {
  return new Promise((ok, fail) => {
    const s = net.createServer();
    s.once('error', fail);
    s.listen(0, '127.0.0.1', () => {
      const { port } = s.address();
      s.close(() => ok(port));
    });
  });
}

const sleep = (ms) => new Promise((ok) => setTimeout(ok, ms));

async function waitFor(what, check, seconds) {
  const end = Date.now() + seconds * 1000;
  let last;
  while (Date.now() < end) {
    try {
      if (await check()) return;
    } catch (e) {
      last = e;
    }
    await sleep(500);
  }
  throw new Error(`Timed out waiting for ${what}${last ? ': ' + last.message : ''}`);
}

function check(path, message) {
  if (!existsSync(path)) throw new Error(`${message} (missing: ${path})`);
}

/** The web server: the built app (one folder per language), with the API behind /api, like Caddy. */
function startWeb(apiPort) {
  const server = createServer(async (req, res) => {
    const url = new URL(req.url, 'http://x');
    if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/q/')) {
      const upstream = await fetch(`http://127.0.0.1:${apiPort}${url.pathname}${url.search}`, {
        method: req.method,
        headers: Object.fromEntries(
          Object.entries(req.headers).filter(([k]) => !['connection', 'keep-alive', 'transfer-encoding', 'upgrade', 'host', 'expect'].includes(k)),
        ),
        body: ['GET', 'HEAD'].includes(req.method) ? undefined : req,
        duplex: 'half',
        redirect: 'manual',
      }).catch(() => null);
      if (!upstream) return void res.writeHead(502).end('API not reachable');
      const headers = {};
      upstream.headers.forEach((v, k) => {
        if (!['content-encoding', 'transfer-encoding', 'connection', 'set-cookie'].includes(k)) headers[k] = v;
      });
      const cookies = upstream.headers.getSetCookie();
      if (cookies.length) headers['set-cookie'] = cookies;
      res.writeHead(upstream.status, headers);
      return void res.end(Buffer.from(await upstream.arrayBuffer()));
    }
    try {
      let file = join(webRoot, normalize(decodeURIComponent(url.pathname)));
      if (!file.startsWith(webRoot)) throw new Error('outside root');
      if ((await stat(file).catch(() => null))?.isDirectory()) file = join(file, 'index.html');
      let info = await stat(file).catch(() => null);
      if (!info) {
        // an app route such as /en/login: the page of that language, the app does the rest
        const lang = url.pathname.split('/')[1];
        file = join(webRoot, ['en', 'pt'].includes(lang) ? lang : 'en', 'index.html');
        info = await stat(file);
      }
      res.writeHead(200, { 'content-type': types[extname(file)] ?? 'application/octet-stream', 'cache-control': 'no-store' });
      res.end(await readFile(file));
    } catch {
      res.writeHead(404).end('not found');
    }
  });
  return new Promise((ok) => server.listen(0, '127.0.0.1', () => ok({ server, port: server.address().port })));
}

/** Starts everything. Returns the address of the web server and a function that stops it all. */
export async function startStack({ log = () => {} } = {}) {
  check(join(webRoot, 'en', 'index.html'), 'The app is not built: run "npm run build" in App/');
  check(join(apiDir, 'build', 'quarkus-app', 'quarkus-run.jar'), 'The API is not built: run "./gradlew quarkusBuild" in API/');
  check(join(repoDir, 'build', 'bundle', 'MANIFEST.json'), 'The data bundle is missing: see API/README.md');
  check(join(repoDir, 'resources', 'card-art'), 'resources/ is missing: run "node tools/extract.mjs"');

  const cleanups = [];
  const stop = async () => {
    for (const c of cleanups.reverse()) {
      try {
        await c();
      } catch {
        /* nothing to do */
      }
    }
  };

  try {
    // --- the database: a new, empty database on a PostgreSQL server that is already running
    // (starting a container from here is unreliable with Docker Engine 29, see API/README.md and Appendix D #50 of the specification)
    const admin = new URL(process.env.POKEDND_E2E_PG ?? 'postgres://test:test@127.0.0.1:55432/postgres');
    const dbName = `pokednd_e2e_${randomBytes(4).toString('hex')}`;
    const dbUser = decodeURIComponent(admin.username);
    const dbPassword = decodeURIComponent(admin.password);
    const client = new pg.Client({ connectionString: admin.href, connectionTimeoutMillis: 5000 });
    try {
      await client.connect();
      await client.query(`CREATE DATABASE ${dbName}`);
    } catch (e) {
      throw new Error(
        `Cannot create a database on ${admin.host} (${e.message}). Start a throw-away PostgreSQL first, for example: ` +
          '"docker run -d -p 55432:5432 -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test -e POSTGRES_DB=postgres postgres:17", ' +
          'or point POKEDND_E2E_PG at one (postgres://user:password@host:port/postgres).',
      );
    }
    cleanups.push(async () => {
      await client.query(`DROP DATABASE IF EXISTS ${dbName} WITH (FORCE)`);
      await client.end();
    });
    const jdbc = `jdbc:postgresql://${admin.hostname}:${admin.port || 5432}/${dbName}`;
    log(`using a new database ${dbName} on ${admin.host}`);

    // --- the API
    const { privateKey, publicKey } = generateKeyPairSync('rsa', {
      modulusLength: 2048,
      privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
      publicKeyEncoding: { type: 'spki', format: 'pem' },
    });
    const dataDir = mkdtempSync(join(tmpdir(), 'pokednd-e2e-data-'));
    cleanups.push(() => rmSync(dataDir, { recursive: true, force: true }));
    const apiPort = await freePort();
    mkdirSync(join(appDir, 'test-results'), { recursive: true });
    const apiLog = createWriteStream(join(appDir, 'test-results', 'api.log'));
    log(`starting the API on port ${apiPort}`);
    const api = spawn('java', ['-jar', join(apiDir, 'build', 'quarkus-app', 'quarkus-run.jar')], {
      cwd: apiDir,
      env: {
        ...process.env,
        QUARKUS_HTTP_HOST: '127.0.0.1',
        QUARKUS_HTTP_PORT: String(apiPort),
        QUARKUS_DATASOURCE_JDBC_URL: jdbc,
        QUARKUS_DATASOURCE_USERNAME: dbUser,
        QUARKUS_DATASOURCE_PASSWORD: dbPassword,
        POKEDND_JWT_PRIVATE_KEY: privateKey.replace(/\n/g, '\\n'),
        POKEDND_JWT_PUBLIC_KEY: publicKey.replace(/\n/g, '\\n'),
        // every test comes from the same address, so the production limits (10 sign-ups an hour) would stop the suite
        POKEDND_AUTH_RATE_LIMIT_LOGIN_PER_IP_PER_MINUTE: '10000',
        POKEDND_AUTH_RATE_LIMIT_LOGIN_FAILURES_PER_EMAIL: '10000',
        POKEDND_AUTH_RATE_LIMIT_REGISTER_PER_IP_PER_HOUR: '10000',
        // the test browser talks plain http to localhost
        POKEDND_AUTH_COOKIE_SECURE: 'false',
        // The tests register and log in at once; there is no mail server (the verification mail just fails to send and is logged)
        POKEDND_AUTH_REQUIRE_EMAIL_VERIFICATION: 'false',
        POKEDND_BUNDLE_DIR: join(repoDir, 'build', 'bundle'),
        POKEDND_RESOURCES_DIR: join(repoDir, 'resources'),
        POKEDND_DATA_DIR: dataDir,
      },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    api.stdout.pipe(apiLog);
    api.stderr.pipe(apiLog);
    let apiExited = false;
    api.once('exit', () => (apiExited = true));
    cleanups.push(() => api.kill());
    await waitFor('the API', async () => {
      if (apiExited) throw new Error('the API stopped; see test-results/api.log');
      const r = await fetch(`http://127.0.0.1:${apiPort}/q/health/ready`);
      return r.ok;
    }, 180);

    // --- the web server
    const web = await startWeb(apiPort);
    cleanups.push(() => new Promise((ok) => web.server.close(ok)));
    const url = `http://127.0.0.1:${web.port}`;
    log(`ready: ${url}`);
    return { url, apiUrl: `http://127.0.0.1:${apiPort}`, stop };
  } catch (e) {
    await stop();
    throw e;
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const stack = await startStack({ log: (m) => console.log(m) });
  console.log(`Open ${stack.url}/en/ (or /pt/). Ctrl+C stops everything.`);
  const bye = async () => {
    await stack.stop();
    process.exit(0);
  };
  process.on('SIGINT', bye);
  process.on('SIGTERM', bye);
}
