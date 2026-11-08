// Exports the Pokemon Showdown data that ships inside the Cobblemon jar to plain JSON files (chapter 19.5).
// Usage: node tools/export-showdown.mjs [--jar <path>] [--out Core/src/main/resources/showdown]
//
// Reads data/cobblemon/showdown.zip inside the jar (compiled JavaScript modules) and writes:
//   moves.json, abilities.json, items.json   one entry per id: every plain-data field, plus "handlers": the names of the
//                                            code handlers (onHit, onModifyDamage ...) that cannot be exported
//   natures.json, typechart.json             reference tables, as Showdown has them
//   source.json                              which Cobblemon version it came from and a SHA-256 of each source file
//   LICENSE-showdown.txt                     the MIT notice
//
// The output is committed to git, so CI needs neither the jar nor the network. Updating it is a deliberate act, done
// together with a Cobblemon update. The output is deterministic: sorted keys, one line per entry.
// Z-moves and Max-moves keep their flags but lose their zMove / maxMove detail (those moves are not used).
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { openZip } from './lib/zip.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..');
const cfg = JSON.parse(fs.readFileSync(path.join(here, 'extract.config.json'), 'utf8'));
const args = process.argv.slice(2), opt = {};
for (let i = 0; i < args.length; i += 2) opt[args[i].replace(/^--/, '')] = args[i + 1];
const JAR = path.resolve(opt.jar ?? path.join(repo, cfg.cobblemon.jar));
const OUT = path.resolve(opt.out ?? path.join(repo, 'Core/src/main/resources/showdown'));

const FILES = { moves: 'data/moves.js', abilities: 'data/abilities.js', items: 'data/items.js', natures: 'data/natures.js', typechart: 'data/typechart.js' };
const DROP_KEYS = new Set(['zMove', 'maxMove', 'contestType', 'spritenum', 'desc', 'shortDesc']);

const jar = openZip(JAR);
const sd = openZip(jar.read('data/cobblemon/showdown.zip'));
const work = fs.mkdtempSync(path.join(fs.realpathSync(process.env.TEMP ?? '/tmp'), 'showdown-'));
const require = createRequire(import.meta.url);

function load(file) {
  if (!sd.has(file)) throw new Error('showdown.zip has no ' + file);
  const bytes = sd.read(file);
  const out = path.join(work, file.replace(/\//g, '_'));
  fs.writeFileSync(out, bytes);
  const mod = require(out);
  return { data: mod[Object.keys(mod)[0]], sha256: crypto.createHash('sha256').update(bytes).digest('hex') };
}

// Names of all handler functions anywhere inside an entry (same rule as tools/effect-coverage.mjs).
function handlerNames(o, found = new Set()) {
  if (!o || typeof o !== 'object') return found;
  for (const [k, v] of Object.entries(o)) {
    if (k === 'zMove' || k === 'maxMove') continue;
    if (typeof v === 'function') found.add(k); else handlerNames(v, found);
  }
  return found;
}

// A copy with functions and noise removed and keys sorted.
function clean(v) {
  if (typeof v === 'function' || v === undefined) return undefined;
  if (Array.isArray(v)) return v.map((x) => clean(x) ?? null);
  if (v && typeof v === 'object') {
    const out = {};
    for (const k of Object.keys(v).sort()) {
      if (DROP_KEYS.has(k)) continue;
      const c = clean(v[k]);
      if (c !== undefined) out[k] = c;
    }
    return out;
  }
  return v;
}

function entries(table, withHandlers) {
  const lines = [];
  for (const id of Object.keys(table).sort()) {
    const e = clean(table[id]);
    if (withHandlers) {
      const h = [...handlerNames(table[id])].sort();
      if (h.length) e.handlers = h;
    }
    lines.push(`${JSON.stringify(id)}:${JSON.stringify(e)}`);
  }
  return '{\n' + lines.join(',\n') + '\n}\n';
}

fs.mkdirSync(OUT, { recursive: true });
const sources = {};
for (const [name, file] of Object.entries(FILES)) {
  const { data, sha256 } = load(file);
  sources[file] = sha256;
  const withHandlers = ['moves', 'abilities', 'items'].includes(name);
  fs.writeFileSync(path.join(OUT, name + '.json'), entries(data, withHandlers));
  console.log(`${name}.json: ${Object.keys(data).length} entries`);
}
fs.writeFileSync(path.join(OUT, 'source.json'), JSON.stringify({
  note: 'Written by tools/export-showdown.mjs. Update it together with a Cobblemon update, never by hand.',
  cobblemon: cfg.cobblemon.version,
  sourceFiles: sources,
}, null, 2) + '\n');
fs.writeFileSync(path.join(OUT, 'LICENSE-showdown.txt'), `The data in this folder comes from Pokemon Showdown (https://github.com/smogon/pokemon-showdown),
in the copy that Cobblemon ${cfg.cobblemon.version} ships inside its jar (data/cobblemon/showdown.zip).
Fields that are code (event handlers) are not included; only their names are, under "handlers".

Pokemon Showdown is released under the MIT License:

The MIT License (MIT)

Copyright (c) Guangcong Luo and other Pokemon Showdown contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
`);
fs.rmSync(work, { recursive: true, force: true });
console.log('written to ' + path.relative(repo, OUT));
