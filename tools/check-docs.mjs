// Checks the legal and welcome files (phase 06). Zero dependencies, needs internet unless --offline.
//
// Usage: node tools/check-docs.mjs [--offline]
//
// Prints every check as PASS or FAIL and exits with 0 only if all of them pass:
//   1. LICENSE (and Mod/LICENSE) are byte-identical to the official MPL-2.0 text from mozilla.org.
//   2. Every row of the table in chapter 21.1 of project_v2.md is covered in CREDITS.md or README.md.
//   3. Every link in README.md, CREDITS.md and CONTRIBUTING.md works (web links answer; file links exist).
//   4. The MPL source-file notice in CONTRIBUTING.md is the standard text.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const offline = process.argv.includes('--offline');
const OFFICIAL_MPL = 'https://www.mozilla.org/media/MPL/2.0/index.f75d2927d3c1.txt';
const docs = ['README.md', 'CREDITS.md', 'CONTRIBUTING.md'];

let failed = 0;
const check = (name, ok, detail = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok || !detail ? '' : '  -> ' + detail}`);
  if (!ok) failed++;
};
const read = (f) => fs.readFileSync(path.join(repo, f));
const text = (f) => read(f).toString('utf8');

// 1. Licence text
if (offline) {
  console.log('SKIP  LICENSE vs mozilla.org (--offline)');
} else {
  const official = Buffer.from(await (await fetch(OFFICIAL_MPL)).arrayBuffer());
  for (const f of ['LICENSE', 'Mod/LICENSE']) {
    check(`${f} is byte-identical to the official MPL-2.0 text`, fs.existsSync(path.join(repo, f)) && official.equals(read(f)));
  }
}

// 2. Chapter 21.1 table
const spec = text('project_v2.md');
const section = spec.split('#### 21.1')[1]?.split('#### 21.2')[0] ?? '';
const rows = section.split('\n').filter((l) => l.startsWith('| ') && !l.startsWith('| Material') && !l.startsWith('|---'));
const mentions = {
  'Our code': ['Mozilla Public License 2.0', 'LICENSE'],
  'Pokémon models': ['The Cobblemon Team', 'CC BY-NC 3.0', 'modified', 'https://cobblemon.com', 'https://gitlab.com/cable-mc/cobblemon-assets'],
  'Cobblemon code and data': ['MPL-2.0', 'ported'],
  'Pokémon Showdown': ['Pokémon Showdown', 'MIT'],
  BlueMap: ['BlueMap', 'MIT'],
  SkinRestorer: ['SkinRestorer', 'Lionarius', 'MIT'],
  'Pokémon, characters': ['Nintendo, Creatures Inc. and GAME FREAK inc.', 'fan', 'not for sale'],
  Minecraft: ['Minecraft', 'Mojang', 'Microsoft'],
};
check('chapter 21.1 table has the 8 rows this check knows', rows.length === Object.keys(mentions).length, `found ${rows.length} rows`);
const combined = text('CREDITS.md') + '\n' + text('README.md');
for (const [row, words] of Object.entries(mentions)) {
  const missing = words.filter((w) => !combined.includes(w));
  check(`21.1 row "${row}" appears in CREDITS.md/README.md`, missing.length === 0, 'missing: ' + missing.join(' | '));
}

// 3. Links
for (const f of docs) {
  const md = text(f);
  const links = [...md.matchAll(/\]\(([^)\s]+)\)|<(https?:\/\/[^>\s]+)>/g)].map((m) => m[1] ?? m[2]);
  const unique = [...new Set(links.filter((l) => !l.startsWith('#')))];
  for (const link of unique) {
    if (/^https?:/.test(link)) {
      if (offline) continue;
      let status = 0;
      try {
        const r = await fetch(link, { redirect: 'follow', headers: { 'user-agent': 'Mozilla/5.0 (PokeDnD docs check)' }, signal: AbortSignal.timeout(20000) });
        status = r.status;
      } catch (e) {
        status = String(e.cause?.code ?? e.message);
      }
      check(`${f}: ${link} answers`, typeof status === 'number' && status >= 200 && status < 400, `status ${status}`);
    } else {
      check(`${f}: ${link} exists`, fs.existsSync(path.join(repo, link.split('#')[0])));
    }
  }
}

// 4. Source-file notice
const notice = ['This Source Code Form is subject to the terms of the Mozilla Public', 'License, v. 2.0. If a copy of the MPL was not distributed with this', 'file, You can obtain one at https://mozilla.org/MPL/2.0/.'];
check('CONTRIBUTING.md defines the standard MPL source-file notice', notice.every((l) => text('CONTRIBUTING.md').includes(l)));

process.exit(failed ? 1 : 0);
