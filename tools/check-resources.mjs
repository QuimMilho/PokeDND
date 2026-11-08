// Smoke test of the generated resources/ folder (phase 01). Zero dependencies.
//
// Usage: node tools/check-resources.mjs [--dir resources] [--no-icons]
//   --no-icons   for a folder made with `extract.mjs --no-icons` (CI): do not require the renderer error lists
//
// Prints every check as PASS or FAIL and exits with 0 only if all of them pass. Run it after `node tools/extract.mjs`.
// It checks that the rebuild is complete, not that the data is right (the Core importer does that later).
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { readCsv } from './lib/csv.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..');
const args = process.argv.slice(2);
const dirIdx = args.indexOf('--dir');
const R = path.resolve(dirIdx >= 0 ? args[dirIdx + 1] : path.join(repo, 'resources'));
const cfg = JSON.parse(fs.readFileSync(path.join(here, 'extract.config.json'), 'utf8'));

let failed = 0;
const check = (name, ok, detail = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
  if (!ok) failed++;
};
const exists = (rel) => fs.existsSync(path.join(R, rel));
const subdirs = (rel) => fs.readdirSync(path.join(R, rel), { withFileTypes: true }).filter((d) => d.isDirectory()).map((d) => d.name);
function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out); else out.push(p);
  }
  return out;
}

if (!fs.existsSync(R)) { console.log(`FAIL  ${R} does not exist. Run: node tools/extract.mjs`); process.exit(1); }
console.log(`Checking ${R}\n`);

// 1. the source jar
{
  const jar = path.join(repo, cfg.cobblemon.jar);
  if (!fs.existsSync(jar)) check('Cobblemon jar present', false, jar);
  else {
    const sha = crypto.createHash('sha512').update(fs.readFileSync(jar)).digest('hex');
    check(`Cobblemon ${cfg.cobblemon.version} jar SHA-512 matches extract.config.json`, sha === cfg.cobblemon.sha512);
  }
}

// 2. CSV row counts (rows without the header)
const EXPECTED_ROWS = { 'pokedex.csv': 1025, 'attack.csv': 930, 'pokemon-attacks.csv': 130704, 'item.csv': 390, 'tm.csv': 391 };
for (const [file, want] of Object.entries(EXPECTED_ROWS)) {
  if (!exists(file)) { check(`${file} has ${want} rows`, false, 'file missing'); continue; }
  const got = readCsv(path.join(R, file)).rows.length;
  check(`${file} has ${want} rows`, got === want, `found ${got}`);
}

// 3. models: 905 folders, each with a licence file
const models = 'assets/cobblemon/bedrock/pokemon/models';
if (!exists(models)) check('905 model folders', false, `${models} missing`);
else {
  const dirs = subdirs(models);
  check('905 model folders', dirs.length === 905, `found ${dirs.length}`);
  const noLicense = dirs.filter((d) => !exists(`${models}/${d}/license`));
  check('every model folder has a license file', noLicense.length === 0, noLicense.length ? `missing in: ${noLicense.slice(0, 5).join(', ')}${noLicense.length > 5 ? ', …' : ''}` : '');
}

// 4. error lists of the renderer are empty
const noIcons = args.includes('--no-icons');
for (const f of noIcons ? [] : ['icons/_errors.txt', 'card-art/_errors.txt']) {
  if (!exists(f)) { check(`${f} exists and is empty`, false, 'file missing'); continue; }
  const text = fs.readFileSync(path.join(R, f), 'utf8').trim();
  check(`${f} is empty`, text === '', text ? `${text.split(/\r?\n/).length} line(s), first: ${text.split(/\r?\n/)[0]}` : '');
}

// 5. every poser named by a resolver exists as a JSON file
{
  const base = 'assets/cobblemon/bedrock/pokemon';
  if (!exists(`${base}/resolvers`) || !exists(`${base}/posers`)) check('every poser named by a resolver exists', false, 'resolvers or posers folder missing');
  else {
    const posers = new Set(walk(path.join(R, base, 'posers')).filter((f) => f.endsWith('.json')).map((f) => path.basename(f, '.json')));
    const resolvers = walk(path.join(R, base, 'resolvers')).filter((f) => f.endsWith('.json'));
    const missing = new Map();
    let named = 0;
    for (const f of resolvers) {
      const j = JSON.parse(fs.readFileSync(f, 'utf8'));
      for (const v of j.variations ?? []) {
        if (!v.poser) continue;
        named++;
        const name = String(v.poser).replace(/^[^:]*:/, '');
        if (!posers.has(name)) missing.set(name, path.basename(f));
      }
    }
    const list = [...missing].slice(0, 5).map(([p, f]) => `${p} (${f})`).join(', ');
    check(`every poser named by a resolver exists (${named} references in ${resolvers.length} resolvers, ${posers.size} poser files)`, missing.size === 0, missing.size ? `${missing.size} missing: ${list}` : '');
  }
}

// 6. language files, TM data, attribution
for (const l of cfg.languages) check(`language file ${l}.json exists`, exists(`assets/cobblemon/lang/${l}.json`));
{
  const tms = exists('data/cobblemon/tms') ? fs.readdirSync(path.join(R, 'data/cobblemon/tms')).filter((f) => f.endsWith('.json')).length : 0;
  check('data/cobblemon/tms exists and has TM definitions', tms > 0, `${tms} files`);
}
check('ATTRIBUTION.md exists', exists('ATTRIBUTION.md'));

// 7. no Aprijuice item or icon (removed from the game). The mechanics config file shipped by the jar is not an item.
{
  const allowed = new Set(['data/cobblemon/mechanics/aprijuices.json']);
  const hits = walk(R)
    .map((f) => path.relative(R, f).split(path.sep).join('/'))
    .filter((rel) => /aprijuice/i.test(rel) && !allowed.has(rel));
  check('no Aprijuice file (item or icon) anywhere', hits.length === 0, hits.length ? `${hits.length} found, first: ${hits[0]}` : '');
  let inCsv = 0;
  for (const f of ['item.csv', 'tm.csv']) if (exists(f) && /aprijuice/i.test(fs.readFileSync(path.join(R, f), 'utf8'))) inCsv++;
  check('no Aprijuice row in item.csv or tm.csv', inCsv === 0);
}

console.log(failed ? `\n${failed} check(s) FAILED` : '\nAll checks passed');
process.exit(failed ? 1 : 0);
