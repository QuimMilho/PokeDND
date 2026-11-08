// Tests for the asset check (phase 09). Run: node --test tools/test/
// Part 1 builds a few fake species in a temp folder, breaks one piece at a time and checks what is reported.
// Part 2 runs on the real resources/ (skipped when the folder or its icons are missing) and compares with Appendix C.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { analyse, problems, describeProblem, roster, REQUIRED } from '../lib/assets.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const cli = path.join(repo, 'tools/check-assets.mjs');

// ---------- fixture builder ----------
const pad = (n) => String(n).padStart(4, '0');
function write(file, text = 'x') { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, text); }

// species: [{ dex, name, omit: [...] }]. `omit` names what to leave out or break:
//   resolver model texture shiny animations poser icon cry cry-file license   leave that piece out
//   bad-model-ref   the resolver names a model file that does not exist
//   bad-texture-frame   an animated texture lists a frame that does not exist
//   all   write no files for the species at all (only its Pokedex row)
function makeRoot(species) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'pokednd-assets-'));
  const cob = path.join(root, 'assets/cobblemon'), bed = path.join(cob, 'bedrock/pokemon');
  write(path.join(root, 'pokedex.csv'), '﻿National Dex,Pokémon Name\r\n' + species.map((s) => `${s.dex},${s.name}`).join('\r\n') + '\r\n');
  const sounds = {};
  for (const s of species) {
    const o = new Set(s.omit ?? []);
    if (o.has('all')) continue;
    const slug = s.name.toLowerCase(), folder = `${pad(s.dex)}_${slug}`;
    const model = o.has('bad-model-ref') ? 'cobblemon:nothing_here.geo' : `cobblemon:${slug}.geo`;
    const tex = `cobblemon:textures/pokemon/${folder}/${slug}.png`;
    const texture = o.has('bad-texture-frame') ? { fps: 10, frames: [tex, `cobblemon:textures/pokemon/${folder}/missing_frame.png`] } : tex;
    const variations = [{ aspects: [], model, poser: `cobblemon:${slug}`, texture }];
    if (!o.has('shiny')) variations.push({ aspects: ['shiny'], texture: `cobblemon:textures/pokemon/${folder}/${slug}_shiny.png` });
    if (!o.has('resolver')) write(path.join(bed, 'resolvers', folder, `0_${slug}_base.json`), JSON.stringify({ order: 0, species: `cobblemon:${slug}`, variations }));
    if (!o.has('model')) write(path.join(bed, 'models', folder, `${slug}.geo.json`));
    if (!o.has('license')) write(path.join(bed, 'models', folder, 'license'));
    if (!o.has('bad-model-ref') && o.has('model')) fs.mkdirSync(path.join(bed, 'models', folder), { recursive: true });
    if (!o.has('poser')) write(path.join(bed, 'posers', folder, `${slug}.json`));
    if (!o.has('animations')) write(path.join(bed, 'animations', folder, `${slug}.animation.json`));
    if (!o.has('texture')) write(path.join(cob, 'textures/pokemon', folder, `${slug}.png`));
    if (!o.has('shiny')) write(path.join(cob, 'textures/pokemon', folder, `${slug}_shiny.png`));
    if (!o.has('icon')) write(path.join(root, 'icons', folder, `${slug}.png`));
    if (!o.has('cry')) sounds[`pokemon.${slug}.cry`] = { sounds: [{ name: `cobblemon:pokemon/${slug}/${slug}_cry`, volume: 0.8 }] };
    if (!o.has('cry') && !o.has('cry-file')) write(path.join(cob, 'sounds/pokemon', slug, `${slug}_cry.ogg`));
    // keep an empty folder for pieces that were left out, so "folder exists but is empty" is also covered
    for (const [piece, dir] of [['animations', path.join(bed, 'animations', folder)], ['icon', path.join(root, 'icons', folder)]]) if (o.has(piece)) fs.mkdirSync(dir, { recursive: true });
  }
  write(path.join(cob, 'sounds.json'), JSON.stringify(sounds));
  return root;
}
const find = (r, dex) => r.species.find((s) => s.dex === dex);
const missing = (r, dex) => REQUIRED.filter((p) => !find(r, dex).pieces[p]);

// ---------- part 1: fixtures ----------
test('a complete fake species is complete and is in the roster', () => {
  const root = makeRoot([{ dex: 1, name: 'Alpha' }]);
  const r = analyse({ root });
  assert.equal(find(r, 1).status, 'complete');
  assert.deepEqual(missing(r, 1), []);
  assert.equal(roster(r).length, 1);
  assert.equal(roster(r)[0].forms[0].id, 'alpha_base');
  assert.equal(problems(r).length, 0);
});

// one case for each kind of missing piece: [what is left out, the pieces that must be reported missing]
const cases = [
  ['resolver', ['resolver', 'poser']],
  ['model', ['model']],
  ['texture', ['texture']],
  ['animations', ['animations']],
  ['poser', ['poser']],
  ['icon', ['icon']],
  ['cry', ['cry']],
  ['cry-file', ['cry']],
  ['license', ['license']],
  ['bad-model-ref', ['model']],
  ['bad-texture-frame', ['texture']],
];
for (const [omit, expected] of cases) {
  test(`missing ${omit} is reported as ${expected.join(' + ')} and the species leaves the roster`, () => {
    const root = makeRoot([{ dex: 1, name: 'Alpha' }, { dex: 2, name: 'Beta', omit: [omit] }]);
    const r = analyse({ root });
    assert.equal(find(r, 1).status, 'complete', 'the untouched species stays complete');
    assert.equal(find(r, 2).status, 'incomplete');
    assert.deepEqual(missing(r, 2), REQUIRED.filter((p) => expected.includes(p)));
    assert.deepEqual(roster(r).map((s) => s.dex), [1]);
    assert.match(describeProblem(problems(r)[0]), new RegExp(`Dex 2 Beta .*missing .*${expected[0]}`));
  });
}

test('a missing shiny texture is reported but does not remove the species from the roster', () => {
  const r = analyse({ root: makeRoot([{ dex: 1, name: 'Alpha', omit: ['shiny'] }]) });
  assert.equal(find(r, 1).pieces.shiny, false);
  assert.equal(find(r, 1).status, 'complete');
  assert.equal(problems(r).length, 0);
});

test('a species with no files at all is "none": not in the roster, not a problem for --strict', () => {
  const r = analyse({ root: makeRoot([{ dex: 1, name: 'Alpha' }, { dex: 2, name: 'Beta', omit: ['all'] }]) });
  assert.equal(find(r, 2).status, 'none');
  assert.equal(find(r, 2).folder, null);
  assert.equal(problems(r).length, 0);
});

test('an unknown Dex folder is listed as an orphan', () => {
  const root = makeRoot([{ dex: 1, name: 'Alpha' }]);
  write(path.join(root, 'icons/0999_ghost/ghost.png'));
  assert.deepEqual(analyse({ root }).orphanFolders, ['0999_ghost']);
});

test('skipping icons (CI after extract --no-icons) stops icons from being required', () => {
  const root = makeRoot([{ dex: 1, name: 'Alpha', omit: ['icon'] }]);
  assert.equal(find(analyse({ root }), 1).status, 'incomplete');
  assert.equal(find(analyse({ root, skip: ['icon'] }), 1).status, 'complete');
});

// ---------- part 1b: the command line ----------
const run = (...a) => spawnSync(process.execPath, [cli, ...a], { encoding: 'utf8', timeout: 60000 });

test('CLI: normal run exits 0 and writes the report and the roster', () => {
  const root = makeRoot([{ dex: 1, name: 'Alpha' }, { dex: 2, name: 'Beta', omit: ['animations'] }]), out = path.join(root, 'out');
  const p = run('--dir', root, '--out', out);
  assert.equal(p.status, 0, p.stderr);
  assert.ok(fs.existsSync(path.join(out, 'reports/assets.md')));
  assert.deepEqual(JSON.parse(fs.readFileSync(path.join(out, 'roster.json'), 'utf8')).species.map((s) => s.dex), [1]);
});

test('CLI: --strict fails and names the species and the piece', () => {
  const root = makeRoot([{ dex: 1, name: 'Alpha' }, { dex: 2, name: 'Beta', omit: ['animations'] }]);
  const p = run('--dir', root, '--out', path.join(root, 'out'), '--strict');
  assert.equal(p.status, 1);
  assert.match(p.stderr, /Dex 2 Beta \[0002_beta\]: missing animations/);
  assert.doesNotMatch(p.stderr, /Alpha/);
});

test('CLI: --strict passes when only species with no assets are missing', () => {
  const root = makeRoot([{ dex: 1, name: 'Alpha' }, { dex: 2, name: 'Beta', omit: ['all'] }]);
  assert.equal(run('--dir', root, '--out', path.join(root, 'out'), '--strict').status, 0);
});

test('CLI: --expect fails when a species listed in the roster file is incomplete', () => {
  const good = makeRoot([{ dex: 1, name: 'Alpha' }, { dex: 2, name: 'Beta' }]);
  const listFile = path.join(good, 'out/roster.json');
  assert.equal(run('--dir', good, '--out', path.join(good, 'out')).status, 0);
  assert.equal(run('--dir', good, '--out', path.join(good, 'out2'), '--expect', listFile).status, 0, 'the roster it wrote is accepted');
  // Now Beta loses its poser; the old roster still lists it.
  fs.rmSync(path.join(good, 'assets/cobblemon/bedrock/pokemon/posers/0002_beta'), { recursive: true });
  const p = run('--dir', good, '--out', path.join(good, 'out3'), '--expect', listFile);
  assert.equal(p.status, 1);
  assert.match(p.stderr, /Dex 2 Beta .*missing poser/);
});

test('CLI: unknown option and missing folder give a clear error', () => {
  assert.equal(run('--nope').status, 2);
  const p = run('--dir', path.join(os.tmpdir(), 'pokednd-does-not-exist'));
  assert.equal(p.status, 1);
  assert.match(p.stderr, /no pokedex\.csv/);
});

test('CLI: running twice gives byte-identical files', () => {
  const root = makeRoot([{ dex: 1, name: 'Alpha' }, { dex: 2, name: 'Beta', omit: ['icon'] }, { dex: 3, name: 'Gamma', omit: ['all'] }]);
  const read = (o) => ['reports/assets.md', 'roster.json'].map((f) => fs.readFileSync(path.join(o, f)));
  run('--dir', root, '--out', path.join(root, 'a'));
  run('--dir', root, '--out', path.join(root, 'b'));
  const [a, b] = [read(path.join(root, 'a')), read(path.join(root, 'b'))];
  assert.ok(a[0].equals(b[0]) && a[1].equals(b[1]));
});

// ---------- part 2: the real resources ----------
const real = path.join(repo, 'resources');
const haveReal = fs.existsSync(path.join(real, 'pokedex.csv')) && fs.existsSync(path.join(real, 'icons'));
const skipReal = haveReal ? false : 'resources/ (with icons) not built: run node tools/extract.mjs';

test('real resources: the numbers of Appendix C', { skip: skipReal }, () => {
  const r = analyse({ root: real, ported: path.join(repo, 'tools/ported-posers') });
  const count = (s) => r.species.filter((x) => x.status === s).length;
  assert.equal(r.species.length, 1025);
  assert.equal(r.species.filter((s) => s.folder).length, 905, '905 species with models, resolvers, icons and textures');
  assert.equal(count('none'), 120, '120 species without assets');
  assert.deepEqual(r.species.filter((s) => s.folder && !s.pieces.animations).map((s) => s.folder), ['0761_bounsweet', '0771_pyukumuku'], '2 models without animations');
  assert.equal(count('complete'), 903);
  assert.deepEqual(problems(r).map((p) => p.missing.join()), ['animations', 'animations'], 'nothing else is missing');
  assert.equal(r.species.filter((s) => s.folder && s.pieces.poser).length, 905, 'every poser named by a resolver exists');
  assert.equal(r.posers.ported, 277, 'the 277 ported posers are in resources');
  // Appendix C.4: anims 903 species folders (+ "special"); posers 656 in the jar, 905 once the ported posers are added (+ "special")
  assert.deepEqual(r.folderCounts, { models: 905, resolvers: 905, animations: 903, posers: 905, textures: 905, icons: 905 });
});

test('real resources: the 120 missing species are exactly the list in Appendix C.1', { skip: skipReal }, () => {
  const spec = fs.readFileSync(path.join(repo, 'project_v2.md'), 'utf8');
  const c1 = spec.slice(spec.indexOf('#### C.1'), spec.indexOf('#### C.2'));
  const listed = [];
  for (const line of c1.split('\n')) {
    const m = /^\| \d \| \d+–\d+ \| \d+ \| (.+) \|$/.exec(line);
    if (m && m[1] !== '—') listed.push(...m[1].split(', ').map((x) => parseInt(x, 10)));
  }
  const r = analyse({ root: real });
  assert.deepEqual(r.species.filter((s) => s.status === 'none').map((s) => s.dex), listed.sort((a, b) => a - b));
  assert.equal(listed.length, 120);
});

test('real resources: the roster equals the species Cobblemon marks implemented, minus the two without animations', { skip: skipReal }, () => {
  const r = analyse({ root: real });
  const impl = new Set(r.implemented);
  assert.equal(impl.size, 905);
  assert.deepEqual(roster(r).map((s) => s.dex), [...impl].filter((d) => d !== 761 && d !== 771).sort((a, b) => a - b));
});
