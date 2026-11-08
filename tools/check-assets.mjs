// Asset check and roster (phase 09). Zero dependencies.
//
// Usage: node tools/check-assets.mjs [--dir resources] [--out build] [--strict] [--no-icons] [--expect <roster.json>]
//   --no-icons   the folder was made with `extract.mjs --no-icons` (CI): do not require icons
//
// For every Pokemon in pokedex.csv it checks: resolver, model, texture, shiny texture, animations, poser, icon, cry, licence file.
// Writes:  <out>/reports/assets.md   the report (it reproduces Appendix C of project_v2.md)
//          <out>/roster.json         the playable species and forms (all required pieces present)
// Both are generated, ignored by git, and byte-identical when run twice on the same input.
//
// Exit code: 0 normally. With --strict it is 1 if any species that has some assets is missing a required piece
// (the message names the species and the piece). With --expect it is 1 if any species listed in that roster file is
// not complete. Species with no assets at all are simply not playable and never fail --strict.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { analyse, problems, describeProblem, renderReport, renderRoster } from './lib/assets.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..');
const args = process.argv.slice(2);
const opt = {};
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--strict') opt.strict = true;
  else if (args[i] === '--no-icons') opt.noIcons = true;
  else if (['--dir', '--out', '--expect'].includes(args[i])) opt[args[i].slice(2)] = args[++i];
  else { console.error(`Unknown option ${args[i]}\nUsage: node tools/check-assets.mjs [--dir resources] [--out build] [--strict] [--no-icons] [--expect <roster.json>]`); process.exit(2); }
}
const root = path.resolve(opt.dir ?? path.join(repo, 'resources'));
const out = path.resolve(opt.out ?? path.join(repo, 'build'));
const cfg = JSON.parse(fs.readFileSync(path.join(here, 'extract.config.json'), 'utf8'));

if (!fs.existsSync(path.join(root, 'pokedex.csv'))) {
  console.error(`FAIL  ${root} has no pokedex.csv. Run: node tools/extract.mjs`);
  process.exit(1);
}

const result = analyse({ root, ported: path.join(here, 'ported-posers'), skip: opt.noIcons ? ['icon'] : [] });
const meta = { cobblemon: cfg.cobblemon.version };
fs.mkdirSync(path.join(out, 'reports'), { recursive: true });
fs.writeFileSync(path.join(out, 'reports/assets.md'), renderReport(result, meta));
fs.writeFileSync(path.join(out, 'roster.json'), renderRoster(result, meta));

const sp = result.species;
const count = (s) => sp.filter((x) => x.status === s).length;
console.log(`Checked ${sp.length} species in ${root}`);
console.log(`  complete (the roster): ${count('complete')}   incomplete: ${count('incomplete')}   no assets at all: ${count('none')}`);
console.log(`  required pieces: ${result.required.join(', ')}`);
console.log(`Wrote ${path.relative(process.cwd(), path.join(out, 'reports/assets.md'))} and ${path.relative(process.cwd(), path.join(out, 'roster.json'))}`);

let bad = [];
if (opt.expect) {
  const file = path.resolve(opt.expect);
  if (!fs.existsSync(file)) { console.error(`FAIL  --expect file ${file} does not exist`); process.exit(1); }
  const listed = new Set(JSON.parse(fs.readFileSync(file, 'utf8')).species.map((s) => s.dex));
  bad = problems(result, listed);
  const unknown = [...listed].filter((d) => !sp.some((s) => s.dex === d));
  for (const d of unknown) bad.push({ dex: d, name: '?', folder: null, missing: [], details: {} });
  if (bad.length) console.error(`\nFAIL  ${bad.length} species listed in ${path.basename(file)} are not complete:`);
} else if (opt.strict) {
  bad = problems(result);
  if (bad.length) console.error(`\nFAIL  ${bad.length} species have some assets but are missing a required piece:`);
}
for (const p of bad) console.error('  ' + describeProblem(p));
if (bad.length) {
  console.error('\nFinish the species (see build/reports/assets.md), or remove it from the roster.');
  process.exit(1);
}
