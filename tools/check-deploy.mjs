// Checks the deploy skeleton (phase 07). Zero dependencies, no Docker needed.
//
// Usage: node tools/check-deploy.mjs
//
// Prints every check as PASS or FAIL and exits with 0 only if all of them pass:
//   1. Every variable of the table in chapter 13.5 of project_v2.md is in deploy/.env.example.
//   2. Every variable that docker-compose.yml reads with ${NAME} is in deploy/.env.example.
//   3. Every variable in deploy/.env.example has a comment line directly above it or above its group.
//   4. No value in deploy/.env.example looks like a real secret (secrets must be empty or CHANGE_ME).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (f) => fs.readFileSync(path.join(repo, f), 'utf8');

let failed = 0;
const check = (name, ok, detail = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok || !detail ? '' : '  -> ' + detail}`);
  if (!ok) failed++;
};

// Parse .env.example (active lines only) and remember which ones have a comment right above them.
const envLines = read('deploy/.env.example').split(/\r?\n/);
const vars = new Map(); // name -> { value, commented }
envLines.forEach((line, i) => {
  const m = /^([A-Z][A-Z0-9_]*)=(.*)$/.exec(line);
  if (!m) return;
  // "commented": the previous line is a comment, or the previous line is a variable that is itself commented
  // (a group of related variables shares one comment).
  const prev = envLines[i - 1] ?? '';
  const prevVar = /^([A-Z][A-Z0-9_]*)=/.exec(prev);
  const commented = prev.startsWith('#') || (prevVar && vars.get(prevVar[1])?.commented);
  vars.set(m[1], { value: m[2], commented: !!commented });
});

// 1. The variables of the 13.5 table. "POKEDND_OAUTH_DISCORD_*" and the "…_USERNAME" style rows are expanded by hand.
const required = [
  'POKEDND_BASE_URL',
  'QUARKUS_DATASOURCE_JDBC_URL', 'QUARKUS_DATASOURCE_USERNAME', 'QUARKUS_DATASOURCE_PASSWORD',
  'POKEDND_JWT_PRIVATE_KEY', 'POKEDND_JWT_PUBLIC_KEY',
  'POKEDND_BOOTSTRAP_ADMIN_EMAIL', 'POKEDND_BOOTSTRAP_ADMIN_PASSWORD',
  'POKEDND_OAUTH_DISCORD_CLIENT_ID', 'POKEDND_OAUTH_DISCORD_CLIENT_SECRET',
  'POKEDND_SMTP_HOST', 'POKEDND_SMTP_PORT', 'POKEDND_SMTP_USER', 'POKEDND_SMTP_PASSWORD',
  'POKEDND_VAPID_PUBLIC', 'POKEDND_VAPID_PRIVATE', 'POKEDND_VAPID_SUBJECT',
  'POKEDND_DATA_DIR',
];
const missing = required.filter((v) => !vars.has(v));
check('.env.example has every variable of the 13.5 table', missing.length === 0, 'missing: ' + missing.join(', '));
// The Mongo alternative is shown as a comment because it replaces the three datasource lines.
check('.env.example shows QUARKUS_MONGODB_CONNECTION_STRING', read('deploy/.env.example').includes('QUARKUS_MONGODB_CONNECTION_STRING='));

// 2. Variables the compose file reads
const compose = read('deploy/docker-compose.yml').replace(/\$\$/g, ''); // $${X} is a container variable, not ours
const used = new Set([...compose.matchAll(/\$\{([A-Z][A-Z0-9_]*)/g)].map((m) => m[1]));
const unknown = [...used].filter((v) => !vars.has(v));
check('every ${VAR} in docker-compose.yml is in .env.example', unknown.length === 0, 'missing: ' + unknown.join(', '));

// 3. Comments
const uncommented = [...vars].filter(([, v]) => !v.commented).map(([k]) => k);
check('every variable in .env.example has a comment', uncommented.length === 0, 'no comment: ' + uncommented.join(', '));

// 4. No real secrets
const secretName = /(PASSWORD|SECRET|PRIVATE|TOKEN|KEY)/;
const harmless = new Set(['', 'CHANGE_ME', '${DB_PASSWORD}', '${DB_USER}']);
const leaked = [...vars].filter(([k, v]) => secretName.test(k) && !k.endsWith('_PUBLIC_KEY') && !harmless.has(v.value)).map(([k]) => k);
check('secrets in .env.example are empty or CHANGE_ME', leaked.length === 0, 'looks real: ' + leaked.join(', '));
const text = read('deploy/.env.example');
const values = [...vars.values()].map((v) => v.value);
check('.env.example has no PEM block, JWT or long random string',
  !/-----BEGIN/.test(text) && !/eyJ[A-Za-z0-9_-]{10,}/.test(text) && !values.some((v) => /^[A-Za-z0-9+/=_-]{32,}$/.test(v)));

console.log(failed === 0 ? '\nAll checks passed.' : `\n${failed} check(s) failed.`);
process.exit(failed === 0 ? 0 : 1);
