// The asset check (phase 09): which Pokemon have everything the game needs, and which do not.
// Pure functions over a `resources/` folder, so the tests can run them on a small fixture folder.
//
//   analyse({ root, ported })  -> result (see below)
//   renderReport(result)       -> markdown text for build/reports/assets.md
//   renderRoster(result, meta) -> text for build/roster.json
//   problems(result, expected) -> list of { dex, name, folder, missing[] } that break --strict / --expect
//
// Everything is sorted and has no dates, so the same input always gives byte-identical output.
import fs from 'node:fs';
import path from 'node:path';
import { readCsvObjects } from './csv.mjs';

// What a species needs before it can be played (spec 19.3 and phase 84). The shiny texture is reported but not required.
export const REQUIRED = ['resolver', 'model', 'texture', 'animations', 'poser', 'icon', 'cry', 'license'];
export const OPTIONAL = ['shiny'];
export const PIECES = [...REQUIRED, ...OPTIONAL];

export const GENERATIONS = [
  [1, 1, 151], [2, 152, 251], [3, 252, 386], [4, 387, 493], [5, 494, 649], [6, 650, 721], [7, 722, 809], [8, 810, 905], [9, 906, 1025],
];

// Cobblemon names one cry after a different word than the species id. Everything else follows "pokemon.<species>[_<form>].cry".
const CRY_ALIASES = { eiscue: 'eiscueice' };

const sortStr = (a, b) => (a < b ? -1 : a > b ? 1 : 0);
const isDir = (p) => { try { return fs.statSync(p).isDirectory(); } catch { return false; } };
const isFile = (p) => { try { return fs.statSync(p).isFile(); } catch { return false; } };
const listDir = (p) => (isDir(p) ? fs.readdirSync(p).sort(sortStr) : []);
const folderRe = /^(\d{4})_(.+)$/;
const stripNs = (s) => String(s).replace(/^[a-z0-9_.-]+:/, '');

export function analyse({ root, ported = null, skip = [] }) {
  const required = REQUIRED.filter((p) => !skip.includes(p)); // `skip: ['icon']` for a rebuild made with extract.mjs --no-icons
  const cob = path.join(root, 'assets/cobblemon');
  const bedrock = path.join(cob, 'bedrock/pokemon');
  const dirs = {
    models: path.join(bedrock, 'models'), resolvers: path.join(bedrock, 'resolvers'), animations: path.join(bedrock, 'animations'),
    posers: path.join(bedrock, 'posers'), textures: path.join(cob, 'textures/pokemon'), icons: path.join(root, 'icons'),
  };
  const dex = readCsvObjects(path.join(root, 'pokedex.csv')).map((r) => ({ dex: Number(r['National Dex']), name: r['Pokémon Name'] }));
  const sounds = isFile(path.join(cob, 'sounds.json')) ? JSON.parse(fs.readFileSync(path.join(cob, 'sounds.json'), 'utf8')) : {};

  // Every species folder that exists anywhere, by Dex number.
  const folderNames = new Set();
  for (const d of Object.values(dirs)) for (const n of listDir(d)) if (folderRe.test(n) && isDir(path.join(d, n))) folderNames.add(n);
  const byDex = new Map();
  for (const f of [...folderNames].sort(sortStr)) {
    const n = Number(f.slice(0, 4));
    if (!byDex.has(n)) byDex.set(n, []);
    byDex.get(n).push(f);
  }

  // Indexes of files that references can point at (by name, wherever the file lives, including the shared "special" folders).
  const modelFiles = new Set(), poserFiles = new Set();
  for (const d of listDir(dirs.models)) for (const f of listDir(path.join(dirs.models, d))) if (f.endsWith('.geo.json')) modelFiles.add(f.slice(0, -5));
  for (const d of listDir(dirs.posers)) for (const f of listDir(path.join(dirs.posers, d))) if (f.endsWith('.json')) poserFiles.add(f.slice(0, -5));
  const textureExists = (ref) => isFile(path.join(cob, stripNs(ref)));

  const species = [];
  const orphanFolders = [];
  for (const [n, fs_] of byDex) if (!dex.some((d) => d.dex === n)) orphanFolders.push(...fs_);

  for (const d of dex) {
    const folders = byDex.get(d.dex) ?? [];
    const rec = { dex: d.dex, name: d.name, folder: folders[0] ?? null, status: 'none', pieces: {}, details: {}, forms: [], species: null, extraFolders: folders.slice(1) };
    species.push(rec);
    if (!folders.length) { for (const p of PIECES) rec.pieces[p] = false; continue; }
    const f = rec.folder;
    const has = (dir, test) => listDir(path.join(dirs[dir], f)).some(test);

    // Resolvers: one file per form. Read what each form names.
    const resolverNames = listDir(path.join(dirs.resolvers, f)).filter((x) => x.endsWith('.json'));
    const badModel = new Set(), badPoser = new Set(), badTexture = new Set();
    let poserRefs = 0, shiny = false;
    const speciesIds = new Set();
    for (const file of resolverNames) {
      let j;
      try { j = JSON.parse(fs.readFileSync(path.join(dirs.resolvers, f, file), 'utf8')); } catch (e) { (rec.details.resolver ??= []).push(`${file}: not valid JSON`); continue; }
      if (j.species) speciesIds.add(stripNs(j.species));
      const vars = Array.isArray(j.variations) ? j.variations : [];
      for (const v of vars) {
        if (v.model && !modelFiles.has(stripNs(v.model))) badModel.add(v.model);
        if (v.poser) { poserRefs++; if (!poserFiles.has(stripNs(v.poser))) badPoser.add(v.poser); }
        if (v.texture) {
          const frames = typeof v.texture === 'string' ? [v.texture] : (v.texture.frames ?? []);
          for (const t of frames) if (!textureExists(t)) badTexture.add(t);
          if ((v.aspects ?? []).includes('shiny') && frames.length && frames.every(textureExists)) shiny = true;
        }
      }
      rec.forms.push({
        id: file.replace(/\.json$/, '').replace(/^\d+_/, ''), file,
        aspects: Array.isArray(vars[0]?.aspects) ? [...vars[0].aspects] : [],
      });
    }
    rec.species = [...speciesIds].sort(sortStr)[0] ?? f.slice(5);

    // The pieces.
    const note = (piece, text) => (rec.details[piece] ??= []).push(text);
    rec.pieces.resolver = resolverNames.length > 0;
    if (!rec.pieces.resolver) note('resolver', 'no resolver file');

    const hasGeo = has('models', (x) => x.endsWith('.geo.json'));
    rec.pieces.model = hasGeo && badModel.size === 0;
    if (!hasGeo) note('model', 'no .geo.json model file');
    for (const m of [...badModel].sort(sortStr)) note('model', `resolver names ${m}, which does not exist`);

    const hasPng = has('textures', (x) => x.endsWith('.png'));
    rec.pieces.texture = hasPng && badTexture.size === 0;
    if (!hasPng) note('texture', 'no .png texture');
    for (const t of [...badTexture].sort(sortStr)) note('texture', `resolver names ${t}, which does not exist`);

    rec.pieces.shiny = shiny;
    if (!shiny) note('shiny', 'no shiny texture');

    rec.pieces.animations = has('animations', (x) => x.endsWith('.animation.json'));
    if (!rec.pieces.animations) note('animations', 'no .animation.json file');

    rec.pieces.poser = poserRefs > 0 && badPoser.size === 0;
    if (resolverNames.length && poserRefs === 0) note('poser', 'no resolver names a poser');
    for (const p of [...badPoser].sort(sortStr)) note('poser', `resolver names ${p}, which does not exist`);

    rec.pieces.icon = has('icons', (x) => x.endsWith('.png'));
    if (!rec.pieces.icon) note('icon', 'no icon .png');

    const cryName = CRY_ALIASES[rec.species] ?? rec.species;
    const cryKeys = Object.keys(sounds).filter((k) => k.startsWith('pokemon.') && k.endsWith('.cry') && (k.slice(8, -4) === cryName || k.slice(8, -4).startsWith(cryName + '_')));
    let cryFiles = 0;
    for (const k of cryKeys) {
      for (const s of sounds[k].sounds ?? []) {
        const file = path.join(cob, 'sounds', stripNs(typeof s === 'string' ? s : s.name) + '.ogg');
        if (isFile(file)) cryFiles++; else note('cry', `${k} names a sound file that does not exist`);
      }
    }
    rec.pieces.cry = cryKeys.length > 0 && cryFiles > 0;
    if (!cryKeys.length) note('cry', `no "pokemon.${cryName}.cry" in sounds.json`);

    rec.pieces.license = isFile(path.join(dirs.models, f, 'license'));
    if (!rec.pieces.license) note('license', 'no license file in the model folder');

    if (f !== rec.folder || rec.extraFolders.length) note('resolver', `more than one folder for Dex ${d.dex}: ${folders.join(', ')}`);

    const missing = required.filter((p) => !rec.pieces[p]);
    rec.status = missing.length ? 'incomplete' : 'complete';
  }

  // Cobblemon's own opinion: species JSON files marked "implemented".
  const implemented = new Set();
  const speciesRoot = path.join(root, 'data/cobblemon/species');
  (function walk(dir) {
    for (const e of fs.existsSync(dir) ? fs.readdirSync(dir, { withFileTypes: true }) : []) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.name.endsWith('.json')) { try { const j = JSON.parse(fs.readFileSync(p, 'utf8')); if (j.implemented && j.nationalPokedexNumber) implemented.add(j.nationalPokedexNumber); } catch { /* not a species file */ } }
    }
  })(speciesRoot);

  // Posers: how many the resolvers name, and how many of those files are our ports.
  let portedCount = 0, posersInResources = 0;
  for (const d of listDir(dirs.posers)) for (const f of listDir(path.join(dirs.posers, d))) {
    if (!f.endsWith('.json')) continue;
    posersInResources++;
    if (ported && isFile(path.join(ported, d, f))) portedCount++;
  }

  const count = (d) => listDir(dirs[d]).filter((n) => folderRe.test(n) && isDir(path.join(dirs[d], n))).length;
  const folderCounts = Object.fromEntries(Object.keys(dirs).map((d) => [d, count(d)]));

  return {
    required, species, orphanFolders, implemented: [...implemented].sort((a, b) => a - b), folderCounts,
    posers: { files: posersInResources, ported: portedCount },
  };
}

export function problems(result, expected = null) {
  const out = [];
  for (const s of result.species) {
    const missing = result.required.filter((p) => !s.pieces[p]);
    const asked = expected ? expected.has(s.dex) : s.folder !== null;
    if (asked && (s.status !== 'complete' || missing.length)) out.push({ dex: s.dex, name: s.name, folder: s.folder, missing, details: s.details });
  }
  return out;
}

export function describeProblem(p) {
  const why = p.missing.map((m) => `${m}${p.details[m]?.length ? ` (${p.details[m].join('; ')})` : ''}`).join(', ');
  return `Dex ${p.dex} ${p.name}${p.folder ? ` [${p.folder}]` : ''}: missing ${why || 'all assets'}`;
}

export function roster(result) {
  return result.species.filter((s) => s.status === 'complete').map((s) => ({ dex: s.dex, name: s.name, folder: s.folder, species: s.species, forms: s.forms }));
}

export function renderRoster(result, meta = {}) {
  const list = roster(result);
  return JSON.stringify({
    generatedBy: 'tools/check-assets.mjs', cobblemon: meta.cobblemon ?? null,
    requires: result.required, speciesCount: list.length, formCount: list.reduce((n, s) => n + s.forms.length, 0), species: list,
  }, null, 2) + '\n';
}

const bt = (s) => '`' + s + '`';

export function renderReport(result, meta = {}) {
  const L = [];
  const sp = result.species;
  const complete = sp.filter((s) => s.status === 'complete');
  const incomplete = sp.filter((s) => s.status === 'incomplete');
  const none = sp.filter((s) => s.status === 'none');
  const withFolders = (piece) => sp.filter((s) => s.folder && s.pieces[piece]).length;
  const formsTotal = sp.reduce((n, s) => n + s.forms.length, 0);

  L.push('# Asset report', '', `Generated by ${bt('tools/check-assets.mjs')}${meta.cobblemon ? ` from Cobblemon ${meta.cobblemon}` : ''}. Do not edit: run the tool again.`, '');
  L.push('A species is **playable** (in the roster) when it has all of: ' + result.required.map(bt).join(', ') + '. The shiny texture is reported but not required.', '');

  L.push('## Summary', '', '| | Species |', '|---|--:|');
  L.push(`| In the Pokédex | ${sp.length} |`, `| With a model | ${sp.filter((s) => s.folder && s.pieces.model).length} |`);
  L.push(`| **Complete (the roster)** | **${complete.length}** |`, `| Incomplete (some assets, not all) | ${incomplete.length} |`, `| No assets at all | ${none.length} |`, `| Forms in the roster | ${complete.reduce((n, s) => n + s.forms.length, 0)} of ${formsTotal} |`, '');

  L.push('## Which pieces exist', '', '| Piece | Species that have it |', '|---|--:|');
  for (const p of PIECES) L.push(`| ${bt(p)}${result.required.includes(p) ? '' : OPTIONAL.includes(p) ? ' *(optional)*' : ''} | ${withFolders(p)} |`);
  L.push('');

  L.push('## C.1 Species with no assets', '', `${none.length} species have no model, texture, resolver or icon. They are not playable until their assets exist.`, '', '| Generation | Dex range | Missing | Species |', '|---|---|--:|---|');
  for (const [g, a, b] of GENERATIONS) {
    const m = none.filter((s) => s.dex >= a && s.dex <= b);
    L.push(`| ${g} | ${a}–${b} | ${m.length} | ${m.length ? m.map((s) => `${s.dex} ${s.name}`).join(', ') : '—'} |`);
  }
  L.push('');

  L.push('## C.2 Models without animations', '');
  const noAnim = sp.filter((s) => s.folder && !s.pieces.animations);
  L.push(noAnim.length ? noAnim.map((s) => bt(s.folder)).join(', ') : 'None.', '');

  L.push('## Incomplete species', '');
  if (!incomplete.length) L.push('None.', '');
  else {
    L.push('| Dex | Species | Missing | Details |', '|--:|---|---|---|');
    for (const s of incomplete) {
      const miss = result.required.filter((p) => !s.pieces[p]);
      L.push(`| ${s.dex} | ${s.name} | ${miss.map(bt).join(', ')} | ${miss.flatMap((m) => s.details[m] ?? []).join('; ') || '—'} |`);
    }
    L.push('');
  }

  L.push('## Posers', '', `${result.posers.files} poser files in ${bt('resources/')}, of which ${result.posers.ported} are ports of Cobblemon's Kotlin classes (${bt('tools/ported-posers/')}). `
    + `Every poser named by a resolver exists for ${sp.filter((s) => s.folder && s.pieces.poser).length} of ${sp.filter((s) => s.folder).length} species with a folder.`, '');

  L.push('## Species without a shiny texture', '');
  const noShiny = sp.filter((s) => s.folder && !s.pieces.shiny);
  L.push(noShiny.length ? noShiny.map((s) => `${s.dex} ${s.name}`).join(', ') : 'None.', '');

  L.push('## Cobblemon\'s own flag', '', `${result.implemented.length} species files are marked ${bt('implemented')} by Cobblemon.`);
  const impl = new Set(result.implemented);
  const a = complete.filter((s) => !impl.has(s.dex)).map((s) => `${s.dex} ${s.name}`);
  const b = sp.filter((s) => impl.has(s.dex) && s.status !== 'complete').map((s) => `${s.dex} ${s.name}`);
  L.push('', `- Complete here but not marked by Cobblemon: ${a.length ? a.join(', ') : 'none'}`, `- Marked by Cobblemon but not complete here: ${b.length ? b.join(', ') : 'none'}`, '');

  L.push('## C.4 Folder counts', '', '| Folder | Species folders |', '|---|--:|');
  for (const [k, v] of Object.entries(result.folderCounts)) L.push(`| ${k} | ${v} |`);
  L.push('', `*(${bt('anims')} and ${bt('posers')} also hold a shared ${bt('special')} folder, which is not counted above.)*`, '');
  if (result.orphanFolders.length) L.push('## Folders with a Dex number that is not in the Pokédex', '', result.orphanFolders.map(bt).join(', '), '');

  return L.join('\n').replace(/\n{3,}/g, '\n\n');
}
