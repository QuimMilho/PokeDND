// Rebuilds resources/ from the Cobblemon jar, so that folder does not have to be stored in git (decided, Appendix E gap 17).
//
// Usage: node tools/extract.mjs [--jar <path>] [--out resources] [--clean] [--no-icons] [--card-art <from>-<to>]
//   --jar        the Cobblemon jar (default: the one named in tools/extract.config.json, next to the repository root).
//                If it does not exist it is downloaded from Modrinth. Its SHA-512 is always checked.
//   --out        output folder (default: resources)
//   --clean      empty the output folder first (only allowed for folders inside this repository)
//   --no-icons   skip rendering the Pokemon icons (the slowest step)
//   --card-art   also render high-resolution card art for a range of Pokedex numbers, e.g. 1-151 (1024 px, no shiny)
//
// What it does (in order): copy the Pokemon assets, species data, language files, TM definitions, item scripts and licence from the jar;
// extract the item icons (no Aprijuice: removed from the game); composite the TM icons; add the ported posers from tools/ported-posers;
// make sure every model folder has a licence file; write the four CSVs (+ tm.csv) with tools/build-csv.mjs; render the icons.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { openZip } from './lib/zip.mjs';
import { decodePng, encodePng } from './lib/png.mjs';
import { stageShowdown } from './lib/showdown.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..');
const cfg = JSON.parse(fs.readFileSync(path.join(here, 'extract.config.json'), 'utf8'));
const args = process.argv.slice(2), opt = {};
for (let i = 0; i < args.length; i++) {
  if (['--clean', '--no-icons'].includes(args[i])) opt[args[i].slice(2)] = true;
  else if (args[i].startsWith('--')) opt[args[i].slice(2)] = args[++i];
}
const JAR = path.resolve(opt.jar ?? path.join(repo, cfg.cobblemon.jar));
const OUT = path.resolve(opt.out ?? path.join(repo, 'resources'));
const log = (...a) => console.log(...a);

// ---------- 1. the jar (download if missing, always verify) ----------
if (!fs.existsSync(JAR)) {
  log(`downloading ${cfg.cobblemon.download}`);
  const r = await fetch(cfg.cobblemon.download, { headers: { 'User-Agent': 'PokeDnD-tools (local dev)' } });
  if (!r.ok) throw new Error(`download failed: HTTP ${r.status}`);
  fs.writeFileSync(JAR, Buffer.from(await r.arrayBuffer()));
}
const sha = crypto.createHash('sha512').update(fs.readFileSync(JAR)).digest('hex');
if (sha !== cfg.cobblemon.sha512) throw new Error(`${JAR} is not the expected Cobblemon ${cfg.cobblemon.version} (SHA-512 differs). Update tools/extract.config.json if you meant to change version.`);
const jar = openZip(JAR);
log(`jar ok: Cobblemon ${cfg.cobblemon.version}, ${jar.entries.length} entries`);

// ---------- 2. output folder ----------
if (opt.clean) {
  const inside = path.relative(repo, OUT);
  if (!inside || inside.startsWith('..') || path.isAbsolute(inside)) throw new Error('--clean only works for folders inside the repository');
  fs.rmSync(OUT, { recursive: true, force: true });
}
fs.mkdirSync(OUT, { recursive: true });
const put = (rel, data) => { const f = path.join(OUT, rel); fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, data); };
const counts = {};
const count = (k, n = 1) => { counts[k] = (counts[k] ?? 0) + n; };

// ---------- 3. copy from the jar ----------
const PREFIXES = [
  'assets/cobblemon/bedrock/pokemon/', 'assets/cobblemon/textures/pokemon/', 'assets/cobblemon/sounds/pokemon/',
  'assets/cobblemon/species.schema.json',
  'data/cobblemon/species/', 'data/cobblemon/species_features/', 'data/cobblemon/species_feature_assignments/', 'data/cobblemon/global_species_features/',
  'data/cobblemon/tms/', 'data/cobblemon/bag_items/', 'data/cobblemon/mechanics/',
  'resourcepacks/', 'LICENSE',
  ...cfg.languages.map((l) => `assets/cobblemon/lang/${l}.json`),
];
for (const e of jar.entries) {
  if (e.dir || !PREFIXES.some((p) => e.name.startsWith(p))) continue;
  put(e.name, jar.read(e));
  count(e.name.startsWith('assets/cobblemon/bedrock') ? 'bedrock' : e.name.startsWith('data/cobblemon/species') ? 'species data' : e.name.startsWith('assets/cobblemon/lang') ? 'language files' : e.name.startsWith('data/cobblemon/tms') ? 'TM definitions' : 'other');
}
// sounds.json: keep only the Pokemon entries (same as tools/filter-sounds.mjs)
{
  const all = JSON.parse(jar.text('assets/cobblemon/sounds.json')), keep = {};
  for (const [k, v] of Object.entries(all)) if (k.startsWith('pokemon.')) keep[k] = v;
  put('assets/cobblemon/sounds.json', JSON.stringify(keep));
  log(`sounds.json: kept ${Object.keys(keep).length} of ${Object.keys(all).length}`);
}

// ---------- 4. item icons ----------
// jar folder -> our category folder. 'aprijuice' is deliberately absent (removed from the game).
const ITEM_MAP = { poke_balls: 'poke_balls', medicine: 'medicine', berries: 'berries', battle_items: 'battle_items', held_items: 'held_items', wearable: 'held_items', evolution: 'evolution', mints: 'mints', type_gem: 'type_gems', experience_candy: 'candy', iv_candy: 'candy', pokedexes: 'pokedexes' };
const ITEM_PRE = 'assets/cobblemon/textures/item/';
for (const e of jar.entries) {
  if (e.dir || !e.name.startsWith(ITEM_PRE) || !e.name.endsWith('.png')) continue;
  const parts = e.name.slice(ITEM_PRE.length).split('/');
  if (parts.length !== 2 || !ITEM_MAP[parts[0]]) continue;
  put(`icons/items/${ITEM_MAP[parts[0]]}/${parts[1]}`, jar.read(e)); // later folders overwrite earlier ones with the same name (wearable over held_items), as before
  count('item icons');
}

// ---------- 5. TM icons: type disc + overlay, as Cobblemon's item model layers them ----------
{
  const overlay = decodePng(jar.read(ITEM_PRE + 'tms/tm_overlay.png'));
  for (const e of jar.under(ITEM_PRE + 'tms/')) {
    const m = /\/([a-z]+)_tm\.png$/.exec(e.name); if (!m) continue;
    const disc = decodePng(jar.read(e));
    if (disc.w !== overlay.w || disc.h !== overlay.h) throw new Error(`TM icon ${m[1]}: size differs from the overlay`);
    const out = new Uint8Array(disc.data);
    for (let i = 0; i < out.length; i += 4) { // overlay "over" disc
      const a = overlay.data[i + 3] / 255, b = out[i + 3] / 255, oa = a + b * (1 - a); if (!oa) continue;
      for (let c = 0; c < 3; c++) out[i + c] = Math.round((overlay.data[i + c] * a + out[i + c] * b * (1 - a)) / oa);
      out[i + 3] = Math.round(oa * 255);
    }
    put(`icons/tm/${m[1]}.png`, encodePng(disc.w, disc.h, out)); count('TM icons');
  }
}

// ---------- 6. ported posers (never overwrite a poser from the jar) ----------
{
  const src = path.join(here, 'ported-posers');
  if (fs.existsSync(src)) for (const folder of fs.readdirSync(src, { withFileTypes: true }).filter((d) => d.isDirectory())) {
    for (const f of fs.readdirSync(path.join(src, folder.name)).filter((x) => x.endsWith('.json'))) {
      const rel = `assets/cobblemon/bedrock/pokemon/posers/${folder.name}/${f}`;
      if (fs.existsSync(path.join(OUT, rel))) continue;
      put(rel, fs.readFileSync(path.join(src, folder.name, f))); count('ported posers');
    }
  }
}

// ---------- 7. a licence file in every model folder ----------
{
  const models = path.join(OUT, 'assets/cobblemon/bedrock/pokemon/models'), root = path.join(models, 'license');
  if (!fs.existsSync(root)) throw new Error('the jar has no models/license file to copy');
  for (const d of fs.readdirSync(models, { withFileTypes: true }).filter((x) => x.isDirectory())) {
    const f = path.join(models, d.name, 'license');
    if (!fs.existsSync(f)) { fs.copyFileSync(root, f); count('licence files copied'); }
  }
}

// ---------- 8. attribution ----------
put('ATTRIBUTION.md', fs.readFileSync(path.join(here, 'attribution.template.md'), 'utf8').replace('{{VERSION}}', cfg.cobblemon.version));

// ---------- 9. CSVs ----------
{
  const stage = path.join(repo, 'build/showdown');
  stageShowdown(JAR, stage, cfg.showdownFiles);
  fs.copyFileSync(path.join(OUT, 'assets/cobblemon/lang/en_us.json'), path.join(stage, 'en_us.json'));
  const r = spawnSync(process.execPath, [path.join(here, 'build-csv.mjs'), stage, OUT], { stdio: 'inherit' });
  if (r.status) throw new Error('build-csv.mjs failed');
}

// ---------- 10. icons and card art ----------
const render = (outDir, extra) => { const r = spawnSync(process.execPath, [path.join(here, 'render-icons.mjs'), OUT, outDir, ...extra], { stdio: 'inherit' }); if (r.status) throw new Error('render-icons.mjs failed'); };
if (!opt['no-icons']) render(path.join(OUT, 'icons'), []);
if (opt['card-art']) render(path.join(OUT, 'card-art'), ['--dex', opt['card-art'], '--size', '1024', '--ss', '3', '--no-shiny']);

log('done:', JSON.stringify(counts));
