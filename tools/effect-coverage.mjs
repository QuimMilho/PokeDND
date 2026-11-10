// Effect coverage report: how much of Showdown's move / ability / item behaviour is plain data and how much is code.
// Usage: node tools/effect-coverage.mjs [--jar <path>] [--resources resources] [--out build/reports] [--top 60]
// Reads: the Showdown data inside the Cobblemon jar, resources/{attack,pokemon-attacks,pokedex,item}.csv, and
//        Core/src/main/resources/rules/implemented-effects.json (the scripted moves that Core runs by hand, phase 46)
// Writes: <out>/coverage.md and <out>/coverage.json   (see chapter 19.6 of project_v2.md)
//
// Rules (kept deliberately simple, so the numbers can be trusted):
//   Moves      scripted  = any function anywhere in the move (onHit, basePowerCallback, a nested condition handler ...), or it applies a
//                          volatile / side / slot condition or pseudo-weather that PokeDnD does not implement itself.
//                          PokeDnD implements the 6 major statuses (status:), confusion and flinch natively (project_v2.md 5.6).
//              automatic = everything else. A move that sets weather or terrain is automatic: the weather part is ignored (decided, 7.9).
//              z/max     = Z-moves and Max moves (not in the game).    no data = the move is not in the Showdown data.
//   Implemented  a scripted move is also "implemented" when Core runs it: it is listed under moves in implemented-effects.json, or it has no code
//              of its own and every condition it sets is listed under conditions there (the same rule as MoveBook.handling in Core).
//              The class stays "scripted" (what the data is); "implemented" says who makes it work.
//   Abilities  scripted = has at least one handler function; "no script" = nothing to implement.
//   Items      same rule as abilities, for the items listed in item.csv.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadShowdown } from './lib/showdown.mjs';
import { readCsvObjects } from './lib/csv.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..');
const cfg = JSON.parse(fs.readFileSync(path.join(here, 'extract.config.json'), 'utf8'));
const args = process.argv.slice(2), opt = {};
for (let i = 0; i < args.length; i += 2) opt[args[i].replace(/^--/, '')] = args[i + 1];
const JAR = path.resolve(opt.jar ?? path.join(repo, cfg.cobblemon.jar));
const RES = path.resolve(opt.resources ?? path.join(repo, 'resources'));
const OUT = path.resolve(opt.out ?? path.join(repo, 'build/reports'));
const TOP = +(opt.top ?? 60);

const IMPLEMENTED = JSON.parse(fs.readFileSync(path.join(repo, 'Core/src/main/resources/rules/implemented-effects.json'), 'utf8'));
const norm = (s) => String(s).toLowerCase().replace(/[^a-z0-9]/g, '');
const SD = loadShowdown(JAR, path.join(repo, 'build/showdown'), cfg.showdownFiles);

// ---------- helpers ----------
const SKIP_KEYS = new Set(['zMove', 'maxMove']);
function fnKeys(o, found = new Set()) {
  if (!o || typeof o !== 'object') return found;
  for (const [k, v] of Object.entries(o)) {
    if (SKIP_KEYS.has(k)) continue;
    if (typeof v === 'function') found.add(k); else fnKeys(v, found);
  }
  return found;
}
const NATIVE_VOLATILE = new Set(['confusion', 'flinch']);
function conditionRefs(m) {
  const refs = new Set(), add = (o) => {
    if (!o || typeof o !== 'object') return;
    for (const k of ['volatileStatus', 'sideCondition', 'slotCondition', 'pseudoWeather']) if (typeof o[k] === 'string') refs.add(`${k}:${o[k]}`);
  };
  add(m); add(m.self); add(m.secondary); add(m.secondary?.self);
  for (const s of m.secondaries ?? []) { add(s); add(s.self); }
  return [...refs];
}
function classifyMove(id) {
  const m = SD.Moves[id];
  if (!m) return { cls: 'no data' };
  if (m.isZ || m.isMax) return { cls: 'z/max' };
  const fns = [...fnKeys(m)];
  const unsupported = conditionRefs(m).filter((r) => !(r.startsWith('volatileStatus:') && NATIVE_VOLATILE.has(r.split(':')[1])));
  const ignoresWeather = !!(m.weather || m.terrain);
  if (fns.length || unsupported.length) {
    const implemented = IMPLEMENTED.moves.includes(id) || (!fns.length && unsupported.every((c) => IMPLEMENTED.conditions.includes(c)));
    return { cls: 'scripted', why: [...fns, ...unsupported], implemented };
  }
  return { cls: 'automatic', ignoresWeather };
}
const pct = (a, b) => (b ? (100 * a / b).toFixed(1) + ' %' : '-');
const table = (head, rows) => ['| ' + head.join(' | ') + ' |', '|' + head.map(() => '---').join('|') + '|', ...rows.map((r) => '| ' + r.join(' | ') + ' |')].join('\n');

// ---------- inputs ----------
const attacks = readCsvObjects(path.join(RES, 'attack.csv')).filter((r) => r.move_id);
const learn = readCsvObjects(path.join(RES, 'pokemon-attacks.csv'));
const dex = readCsvObjects(path.join(RES, 'pokedex.csv'));
const items = readCsvObjects(path.join(RES, 'item.csv'));

// playable species = rows marked implemented (the modelled species); older CSVs have a blank value for the rest
const playable = new Set(learn.filter((r) => r.implemented === 'true').map((r) => r.dex));
const learners = new Map(); // move id -> set of dex numbers (playable species only)
let learnRows = 0; const rowsPerMove = new Map();
for (const r of learn) {
  if (r.implemented !== 'true') continue;
  learnRows++;
  rowsPerMove.set(r.move_id, (rowsPerMove.get(r.move_id) ?? 0) + 1);
  (learners.get(r.move_id) ?? learners.set(r.move_id, new Set()).get(r.move_id)).add(r.dex);
}

// ---------- moves ----------
const moves = attacks.map((a) => ({ id: a.move_id, name: a.name, ...classifyMove(a.move_id), species: learners.get(a.move_id)?.size ?? 0, rows: rowsPerMove.get(a.move_id) ?? 0 }));
const mCount = (c) => moves.filter((m) => m.cls === c).length;
const byRows = (a, b) => b.rows - a.rows || a.name.localeCompare(b.name);
const allScripted = moves.filter((m) => m.cls === 'scripted').sort(byRows);
const implementedMoves = allScripted.filter((m) => m.implemented);
const scriptedMoves = allScripted.filter((m) => !m.implemented); // what is still to do
const autoRows = moves.filter((m) => m.cls === 'automatic').reduce((s, m) => s + m.rows, 0);
const implementedRows = implementedMoves.reduce((s, m) => s + m.rows, 0);
const handlerFreq = new Map();
for (const m of scriptedMoves) for (const w of new Set(m.why)) handlerFreq.set(w, (handlerFreq.get(w) ?? 0) + 1);
// rows covered by: automatic moves + the moves Core runs by hand + the next n most-learned scripted moves that are still to do
const cum = (n) => autoRows + implementedRows + scriptedMoves.slice(0, n).reduce((s, m) => s + m.rows, 0);
// the same without Core's own moves: automatic moves + the n most-learned scripted moves (the first measurement, 2026-11-08)
const cumFirst = (n) => autoRows + allScripted.slice(0, n).reduce((s, m) => s + m.rows, 0);

// ---------- abilities ----------
const abilityId = (n) => norm(n);
const abilitySlots = new Map(); // ability id -> species count (playable)
let slots = 0;
for (const r of dex) {
  if (!playable.has(r['National Dex'])) continue;
  for (const col of ['Ability I', 'Ability II', 'Hidden Ability']) {
    if (!r[col]) continue; const id = abilityId(r[col]); slots++;
    abilitySlots.set(id, (abilitySlots.get(id) ?? 0) + 1);
  }
}
const abilities = [...abilitySlots].map(([id, n]) => {
  const a = SD.Abilities[id];
  return { id, name: a?.name ?? id, cls: !a ? 'no data' : fnKeys(a).size ? 'scripted' : 'no script', species: n, handlers: a ? [...fnKeys(a)] : [] };
}).sort((a, b) => b.species - a.species || a.name.localeCompare(b.name));
const scriptedAb = abilities.filter((a) => a.cls === 'scripted');
const noScriptSlots = abilities.filter((a) => a.cls !== 'scripted').reduce((s, a) => s + a.species, 0);
const cumAb = (n) => noScriptSlots + scriptedAb.slice(0, n).reduce((s, a) => s + a.species, 0);

// ---------- items ----------
const SKIP_CATS = new Set(['aprijuice', 'pokedexes']);
const itemRows = items.filter((r) => !SKIP_CATS.has(r.category) && !/overlay/.test(r.item_id)).map((r) => {
  const it = SD.Items[norm(r.item_id)];
  return { id: r.item_id, name: r.name, category: r.category, cls: !it ? 'no data' : fnKeys(it).size ? 'scripted' : 'no script', handlers: it ? [...fnKeys(it)] : [] };
});
const cats = [...new Set(itemRows.map((i) => i.category))].sort();
const catRows = cats.map((c) => { const l = itemRows.filter((i) => i.category === c); return [c, l.length, l.filter((i) => i.cls === 'no script').length, l.filter((i) => i.cls === 'scripted').length, l.filter((i) => i.cls === 'no data').length]; });

// ---------- report ----------
const summary = {
  generatedFrom: cfg.cobblemon.version,
  moves: { total: moves.length, automatic: mCount('automatic'), scripted: mCount('scripted'), implemented: implementedMoves.length, 'still scripted': scriptedMoves.length, 'z/max': mCount('z/max'), 'no data': mCount('no data'), automaticSetsWeatherOrTerrain: moves.filter((m) => m.ignoresWeather).length },
  learnsetRowsPlayable: { total: learnRows, automatic: autoRows, implemented: implementedRows, withImplemented: autoRows + implementedRows, withNext25: cum(25), withNext50: cum(50), withNext100: cum(100), withTop25First: cumFirst(25), withTop50First: cumFirst(50), withTop100First: cumFirst(100) },
  abilities: { usedByPlayableSpecies: abilities.length, scripted: scriptedAb.length, 'no script': abilities.filter((a) => a.cls === 'no script').length, 'no data': abilities.filter((a) => a.cls === 'no data').length, slots },
  items: { total: itemRows.length, 'no script': itemRows.filter((i) => i.cls === 'no script').length, scripted: itemRows.filter((i) => i.cls === 'scripted').length, 'no data': itemRows.filter((i) => i.cls === 'no data').length },
};

const md = [];
md.push(`# Effect coverage\n\nGenerated by \`tools/effect-coverage.mjs\` from the Showdown data inside Cobblemon ${cfg.cobblemon.version} and the CSVs in \`resources/\`. Do not edit by hand.\n`);
md.push('## Summary\n');
md.push(table(['Kind', 'Total', 'Automatic / no script', 'Scripted', 'Other'], [
  ['Moves (attack.csv)', summary.moves.total, `${summary.moves.automatic} (${pct(summary.moves.automatic, summary.moves.total)})`, `${summary.moves.scripted} (${pct(summary.moves.scripted, summary.moves.total)}), of them ${summary.moves.implemented} run by Core and ${summary.moves['still scripted']} still to do`, `${summary.moves['z/max']} Z/Max, ${summary.moves['no data']} no data`],
  ['Abilities used by playable species', summary.abilities.usedByPlayableSpecies, `${summary.abilities['no script']} (${pct(summary.abilities['no script'], summary.abilities.usedByPlayableSpecies)})`, `${summary.abilities.scripted} (${pct(summary.abilities.scripted, summary.abilities.usedByPlayableSpecies)})`, `${summary.abilities['no data']} no data`],
  ['Items (item.csv, without Aprijuice and Pokédexes)', summary.items.total, `${summary.items['no script']} (${pct(summary.items['no script'], summary.items.total)})`, `${summary.items.scripted} (${pct(summary.items.scripted, summary.items.total)})`, `${summary.items['no data']} not in Showdown`],
]));
md.push(`\nHow the classes are decided: see the header of \`tools/effect-coverage.mjs\`. ${summary.moves.automaticSetsWeatherOrTerrain} automatic moves also set weather or terrain; that part is ignored because weather and terrain are not in the game (7.9).\n`);
md.push('## How much play does each choice cover?\n');
md.push(`Counted over the ${learnRows.toLocaleString('en')} (species, move) learnset rows of playable species.\n`);
md.push(table(['If these moves work', 'Learnset rows covered'], [
  ['All automatic moves only', `${autoRows.toLocaleString('en')} (${pct(autoRows, learnRows)})`],
  [`+ the ${implementedMoves.length} scripted moves that Core runs by hand (phase 46)`, `${(autoRows + implementedRows).toLocaleString('en')} (${pct(autoRows + implementedRows, learnRows)})`],
  ['+ the next 25 most-learned scripted moves', `${cum(25).toLocaleString('en')} (${pct(cum(25), learnRows)})`],
  ['+ the next 50', `${cum(50).toLocaleString('en')} (${pct(cum(50), learnRows)})`],
  ['+ the next 100', `${cum(100).toLocaleString('en')} (${pct(cum(100), learnRows)})`],
]));
md.push(`\nFor comparison, the first measurement (before Core ran any scripted move): automatic moves plus the 25 most-learned scripted moves would cover ${pct(cumFirst(25), learnRows)}, the top 50 ${pct(cumFirst(50), learnRows)}, the top 100 ${pct(cumFirst(100), learnRows)}.`);
md.push(`\nAbilities, counted over ${slots.toLocaleString('en')} (species, ability) slots: automatic only ${pct(noScriptSlots, slots)}; + top 25 scripted ${pct(cumAb(25), slots)}; + top 50 ${pct(cumAb(50), slots)}; + top 100 ${pct(cumAb(100), slots)}.\n`);
md.push(`## Scripted moves that Core runs by hand (${implementedMoves.length})\n`);
md.push(table(['#', 'Move', 'Species', 'Learnset rows', 'How'], implementedMoves.map((m, i) => [i + 1, m.name, m.species, m.rows, IMPLEMENTED.moves.includes(m.id) ? 'written in Java' : 'shared condition ' + m.why.map((w) => '`' + w + '`').join(' ')])));
md.push(`\n## Scripted moves still to do (top ${TOP}, by learnset rows)\n`);
md.push(table(['#', 'Move', 'Species', 'Learnset rows', 'Why scripted'], scriptedMoves.slice(0, TOP).map((m, i) => [i + 1, m.name, m.species, m.rows, [...new Set(m.why)].join(', ')])));
md.push('\n## What makes moves scripted\n');
md.push(table(['Handler or condition', 'Moves'], [...handlerFreq].sort((a, b) => b[1] - a[1]).slice(0, 25).map(([k, v]) => [`\`${k}\``, v])));
md.push(`\n## Scripted abilities to implement first (top ${TOP}, by number of playable species)\n`);
md.push(table(['#', 'Ability', 'Species', 'Handlers'], scriptedAb.slice(0, TOP).map((a, i) => [i + 1, a.name, a.species, a.handlers.slice(0, 4).map((h) => `\`${h}\``).join(' ')])));
md.push('\n## Items by category\n');
md.push(table(['Category', 'Items', 'No script', 'Scripted', 'Not in Showdown'], catRows));
md.push('\n### Scripted items\n');
md.push(itemRows.filter((i) => i.cls === 'scripted').map((i) => `- ${i.name} (${i.category}): ${i.handlers.slice(0, 3).map((h) => '`' + h + '`').join(' ')}`).join('\n'));
md.push('\n');

fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(path.join(OUT, 'coverage.md'), md.join('\n'));
fs.writeFileSync(path.join(OUT, 'coverage.json'), JSON.stringify({
  summary,
  moves: Object.fromEntries(moves.map((m) => [m.id, m.cls])),
  implementedMoves: implementedMoves.map((m) => m.id).sort(),
  abilities: Object.fromEntries(abilities.map((a) => [a.id, a.cls])),
  items: Object.fromEntries(itemRows.map((i) => [i.id, i.cls])),
}, null, 1));
console.log(JSON.stringify(summary, null, 1));
console.log('wrote', path.join(OUT, 'coverage.md'));
