// Runs the API test suite once per database flavour (phase 34). Zero dependencies, needs Docker.
//
// Usage: node tools/test-api-flavours.mjs [postgres] [mysql] [mariadb] [mongo] [--keep]
//
// For each flavour it starts a throw-away database container on a free local port, waits until the database answers, runs
//     ./gradlew test -PdbFlavour=<flavour>
// in API/ with the connection in the environment (QUARKUS_DATASOURCE_* or QUARKUS_MONGODB_CONNECTION_STRING), and removes the container.
// It does not use Testcontainers: on Docker Engine 29 Testcontainers hangs (spec Appendix D #50). CI can use Dev Services instead and
// just run `./gradlew test -PdbFlavour=...` in a matrix.
//
// Starting a container can take minutes on some Docker Desktop set-ups, so the waits are long. --keep leaves the containers running.
// Prints one line per flavour and exits with 0 only if every suite passed.
import { spawnSync, spawn } from 'node:child_process';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const api = path.join(repo, 'API');
const args = process.argv.slice(2);
const keep = args.includes('--keep');
const all = ['postgres', 'mysql', 'mariadb', 'mongo'];
const wanted = args.filter((a) => !a.startsWith('--'));
const flavours = wanted.length ? wanted : all;
for (const f of flavours) if (!all.includes(f)) throw new Error('Unknown flavour ' + f + ' (use ' + all.join(', ') + ')');

const PASSWORD = 'test';
const specs = {
  postgres: { image: 'postgres:17', port: 5432, env: ['POSTGRES_USER=test', 'POSTGRES_PASSWORD=test', 'POSTGRES_DB=pokednd_test'], ready: ['pg_isready', '-U', 'test', '-d', 'pokednd_test'],
    connect: (p) => ({ QUARKUS_DATASOURCE_JDBC_URL: `jdbc:postgresql://127.0.0.1:${p}/pokednd_test`, QUARKUS_DATASOURCE_USERNAME: 'test', QUARKUS_DATASOURCE_PASSWORD: PASSWORD }) },
  mysql: { image: 'mysql:8.4', port: 3306, env: ['MYSQL_ROOT_PASSWORD=test', 'MYSQL_DATABASE=pokednd_test', 'MYSQL_USER=test', 'MYSQL_PASSWORD=test'],
    ready: ['mysqladmin', 'ping', '-h', '127.0.0.1', '-uroot', '-ptest', '--silent'],
    connect: (p) => ({ QUARKUS_DATASOURCE_JDBC_URL: `jdbc:mysql://127.0.0.1:${p}/pokednd_test`, QUARKUS_DATASOURCE_USERNAME: 'test', QUARKUS_DATASOURCE_PASSWORD: PASSWORD }) },
  mariadb: { image: 'mariadb:11', port: 3306, env: ['MARIADB_ROOT_PASSWORD=test', 'MARIADB_DATABASE=pokednd_test', 'MARIADB_USER=test', 'MARIADB_PASSWORD=test'],
    ready: ['healthcheck.sh', '--connect', '--innodb_initialized'],
    connect: (p) => ({ QUARKUS_DATASOURCE_JDBC_URL: `jdbc:mariadb://127.0.0.1:${p}/pokednd_test`, QUARKUS_DATASOURCE_USERNAME: 'test', QUARKUS_DATASOURCE_PASSWORD: PASSWORD }) },
  mongo: { image: 'mongo:8', port: 27017, env: [], ready: ['mongosh', '--quiet', '--eval', 'db.adminCommand("ping").ok'],
    connect: (p) => ({ QUARKUS_MONGODB_CONNECTION_STRING: `mongodb://127.0.0.1:${p}` }) },
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const docker = (a, timeout = 600000) => spawnSync('docker', a, { encoding: 'utf8', timeout });
const freePort = () => new Promise((resolve) => {
  const s = net.createServer().listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => resolve(p)); });
});

async function startDatabase(flavour) {
  const spec = specs[flavour];
  const name = `pokednd-test-${flavour}-${process.pid}`;
  const port = await freePort();
  console.log(`[${flavour}] starting ${spec.image} on port ${port} (this can take minutes)`);
  const run = docker(['run', '-d', '--name', name, ...spec.env.flatMap((e) => ['-e', e]), '-p', `127.0.0.1:${port}:${spec.port}`, spec.image]);
  if (run.status !== 0) throw new Error(`docker run failed: ${run.stderr || run.error}`);
  const deadline = Date.now() + 10 * 60 * 1000;
  while (Date.now() < deadline) {
    const r = docker(['exec', name, ...spec.ready], 60000);
    if (r.status === 0 && (flavour !== 'mongo' || r.stdout.trim() === '1')) return { name, port };
    await sleep(3000);
  }
  throw new Error(`the ${flavour} database did not become ready`);
}

function gradle(flavour, env) {
  return new Promise((resolve) => {
    // the full path: a shell that does not search the current folder (Git Bash sets NoDefaultCurrentDirectoryInExePath) cannot find a bare gradlew.bat
    const cmd = process.platform === 'win32' ? `"${path.join(api, 'gradlew.bat')}"` : './gradlew';
    const p = spawn(cmd, ['test', `-PdbFlavour=${flavour}`], { cwd: api, env: { ...process.env, ...env }, stdio: 'inherit', shell: process.platform === 'win32' });
    p.on('exit', (code) => resolve(code));
  });
}

const results = [];
for (const flavour of flavours) {
  let db;
  try {
    db = await startDatabase(flavour);
    const code = await gradle(flavour, specs[flavour].connect(db.port));
    results.push({ flavour, ok: code === 0 });
  } catch (e) {
    console.error(`[${flavour}] ${e.message}`);
    results.push({ flavour, ok: false });
  } finally {
    if (db && !keep) docker(['rm', '-f', db.name], 120000);
  }
}
console.log('\nAPI suite per database:');
for (const r of results) console.log(`  ${r.ok ? 'PASS' : 'FAIL'}  ${r.flavour}`);
process.exit(results.every((r) => r.ok) ? 0 : 1);
