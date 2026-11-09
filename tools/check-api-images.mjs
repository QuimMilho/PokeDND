// Builds the API image of every database flavour, starts it against its database with nothing but environment variables, and asks it
// for /q/health (phase 34). Zero dependencies, needs Docker. Slow: each image builds the API from source.
//
// Usage: node tools/check-api-images.mjs [postgres] [mysql] [mariadb] [mongo] [--no-build]
//
// --no-build uses the images pokednd-api:test-<flavour> that an earlier run left. Containers and the network are removed at the end.
// Exits with 0 only if every image answered /q/health with UP.
import { spawnSync } from 'node:child_process';
import crypto from 'node:crypto';
import netModule from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const noBuild = args.includes('--no-build');
const all = ['postgres', 'mysql', 'mariadb', 'mongo'];
const wanted = args.filter((a) => !a.startsWith('--'));
const flavours = wanted.length ? wanted : all;
for (const f of flavours) if (!all.includes(f)) throw new Error('Unknown flavour ' + f);

const specs = {
  postgres: { image: 'postgres:17', env: ['POSTGRES_USER=test', 'POSTGRES_PASSWORD=test', 'POSTGRES_DB=pokednd'], ready: ['pg_isready', '-U', 'test', '-d', 'pokednd'],
    api: { QUARKUS_DATASOURCE_JDBC_URL: 'jdbc:postgresql://db:5432/pokednd', QUARKUS_DATASOURCE_USERNAME: 'test', QUARKUS_DATASOURCE_PASSWORD: 'test' } },
  mysql: { image: 'mysql:8.4', env: ['MYSQL_ROOT_PASSWORD=test', 'MYSQL_DATABASE=pokednd', 'MYSQL_USER=test', 'MYSQL_PASSWORD=test'], ready: ['mysqladmin', 'ping', '-h', '127.0.0.1', '-uroot', '-ptest', '--silent'],
    api: { QUARKUS_DATASOURCE_JDBC_URL: 'jdbc:mysql://db:3306/pokednd', QUARKUS_DATASOURCE_USERNAME: 'test', QUARKUS_DATASOURCE_PASSWORD: 'test' } },
  mariadb: { image: 'mariadb:11', env: ['MARIADB_ROOT_PASSWORD=test', 'MARIADB_DATABASE=pokednd', 'MARIADB_USER=test', 'MARIADB_PASSWORD=test'], ready: ['healthcheck.sh', '--connect', '--innodb_initialized'],
    api: { QUARKUS_DATASOURCE_JDBC_URL: 'jdbc:mariadb://db:3306/pokednd', QUARKUS_DATASOURCE_USERNAME: 'test', QUARKUS_DATASOURCE_PASSWORD: 'test' } },
  mongo: { image: 'mongo:8', env: [], ready: ['mongosh', '--quiet', '--eval', 'db.adminCommand("ping").ok'],
    api: { QUARKUS_MONGODB_CONNECTION_STRING: 'mongodb://db:27017' } },
};

const freePort = () => new Promise((resolve) => {
  const srv = netModule.createServer().listen(0, '127.0.0.1', () => { const p = srv.address().port; srv.close(() => resolve(p)); });
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const docker = (a, timeout = 1800000) => spawnSync('docker', a, { encoding: 'utf8', timeout });

// A throw-away signing key: the API refuses to start in production without one.
const { privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).trim().replace(/\r?\n/g, '\\n');

const net = `pokednd-images-${process.pid}`;
docker(['network', 'create', net]);
const results = [];
for (const flavour of flavours) {
  const spec = specs[flavour];
  const tag = `pokednd-api:test-${flavour}`;
  const names = [`pnd-db-${flavour}-${process.pid}`, `pnd-api-${flavour}-${process.pid}`];
  let ok = false;
  try {
    if (!noBuild) {
      console.log(`[${flavour}] building ${tag}`);
      const b = docker(['build', '-f', path.join(repo, 'deploy', 'api.Dockerfile'), '--build-arg', `FLAVOUR=${flavour}`, '-t', tag, repo]);
      if (b.status !== 0) throw new Error('docker build failed: ' + (b.stderr || '').split('\n').slice(-8).join('\n'));
    }
    console.log(`[${flavour}] starting the database and the image`);
    let r = docker(['run', '-d', '--name', names[0], '--network', net, '--network-alias', 'db', ...spec.env.flatMap((e) => ['-e', e]), spec.image]);
    if (r.status !== 0) throw new Error('database: ' + r.stderr);
    for (let i = 0; ; i++) {
      if (i > 200) throw new Error('the database did not become ready');
      const p = docker(['exec', names[0], ...spec.ready], 60000);
      if (p.status === 0 && (flavour !== 'mongo' || p.stdout.trim() === '1')) break;
      await sleep(3000);
    }
    const env = {
      ...spec.api,
      POKEDND_JWT_PRIVATE_KEY: pem,
      POKEDND_BASE_URL: 'https://localhost',
      POKEDND_BOOTSTRAP_ADMIN_EMAIL: 'admin@example.test',
      POKEDND_BOOTSTRAP_ADMIN_PASSWORD: 'a long enough test password',
    };
    const port = await freePort();
    r = docker(['run', '-d', '--name', names[1], '--network', net, '-p', `127.0.0.1:${port}:8080`, ...Object.entries(env).flatMap(([k, v]) => ['-e', `${k}=${v}`]), tag]);
    if (r.status !== 0) throw new Error('api: ' + r.stderr);
    for (let i = 0; i < 120 && !ok; i++) {
      try {
        const h = await fetch(`http://127.0.0.1:${port}/q/health`, { signal: AbortSignal.timeout(5000) });
        ok = h.status === 200 && (await h.json()).status === 'UP';
      } catch {
        // not up yet
      }
      if (!ok) await sleep(3000);
    }
    if (!ok) throw new Error('/q/health never answered 200\n' + docker(['logs', '--tail', '50', names[1]]).stdout);
    console.log(`[${flavour}] /q/health answers 200`);
  } catch (e) {
    console.error(`[${flavour}] ${e.message}`);
  } finally {
    docker(['rm', '-f', ...names], 120000);
  }
  results.push({ flavour, ok });
}
docker(['network', 'rm', net]);
console.log('\nAPI image per database:');
for (const r of results) console.log(`  ${r.ok ? 'PASS' : 'FAIL'}  ${r.flavour}`);
process.exit(results.every((r) => r.ok) ? 0 : 1);
