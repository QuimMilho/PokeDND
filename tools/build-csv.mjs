// Builds pokedex.csv, pokemon-attacks.csv, attack.csv, item.csv from Cobblemon data only.
// usage: node build-csv.mjs <stagedSrcDir> <extractedDir>
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';

const [srcDir, root] = process.argv.slice(2);
const require = createRequire(import.meta.url);
const firstExport = (f) => { const m = require(path.join(srcDir, f)); return m[Object.keys(m)[0]]; };
const Pokedex = firstExport('data_pokedex.js');
const Moves = firstExport('data_moves.js');
const MovesText = firstExport('data_text_moves.js');
const ItemsText = firstExport('data_text_items.js');
const lang = JSON.parse(fs.readFileSync(path.join(srcDir, 'en_us.json'), 'utf8'));

const norm = (s) => String(s).toLowerCase().replace(/[^a-z0-9]/g, '');
const title = (s) => String(s).replace(/[_\-:.]+/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()).trim();
const cap = (s) => (s ? s[0].toUpperCase() + s.slice(1) : '');
const clean = (s) => (s == null ? '' : String(s).replace(/\s+/g, ' ').trim());

function csv(rows) {
  const q = (v) => {
    const s = v == null ? '' : String(v);
    return /[",\r\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  };
  return '﻿' + rows.map((r) => r.map(q).join(',')).join('\r\n') + '\r\n';
}
const write = (name, rows) => {
  fs.writeFileSync(path.join(root, name), csv(rows), 'utf8');
  console.log(`${name}: ${rows.length - 1} rows`);
};

// ---- species ----
const speciesDir = path.join(root, 'data', 'cobblemon', 'species');
const species = [];
for (const gen of fs.readdirSync(speciesDir)) {
  const d = path.join(speciesDir, gen);
  if (!fs.statSync(d).isDirectory()) continue;
  for (const f of fs.readdirSync(d)) {
    if (!f.endsWith('.json')) continue;
    const j = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8'));
    j._id = f.slice(0, -5);
    species.push(j);
  }
}
species.sort((a, b) => a.nationalPokedexNumber - b.nationalPokedexNumber || a._id.localeCompare(b._id));
const spName = (id) => lang[`cobblemon.species.${id}.name`] || cap(id);
const abName = (id) => lang[`cobblemon.ability.${id}`] || title(id);
const itemName = (id) => lang[`item.cobblemon.${id}`] || title(id);

// ---- evolution text ----
const namesp = (s) => String(s).replace(/^#?[a-z_]+:/, '');
function req(r) {
  switch (r.variant) {
    case 'level': return `Level ${r.minLevel ?? r.amount ?? '?'}`;
    case 'friendship': return `Friendship >= ${r.amount}`;
    case 'time_range': return cap(r.range);
    case 'held_item': return `Holding ${itemName(namesp(r.itemCondition))}`;
    case 'has_move': return `Knows ${title(r.move)}`;
    case 'has_move_type': return `Knows a ${cap(r.type)}-type move`;
    case 'use_move': return `Use ${title(r.move)} x${r.amount}`;
    case 'biome': return `Biome ${title(namesp(r.biomeCondition))}`;
    case 'structure': return `Structure ${title(namesp(r.structureCondition))}`;
    case 'weather': return r.isRaining ? 'Raining' : r.isThundering ? 'Thundering' : 'Weather';
    case 'moon_phase': return `Moon ${title(r.moonPhase)}`;
    case 'party_member': return `${r.contains === false ? 'Without' : 'With'} ${title(r.target)} in party`;
    case 'blocks_traveled': return `Travel ${r.amount} blocks`;
    case 'stat_compare': return `${title(r.highStat)} > ${title(r.lowStat)}`;
    case 'stat_equal': return `${title(r.statOne)} = ${title(r.statTwo)}`;
    case 'property_range': return `${title(r.feature)} ${r.range}`;
    case 'properties': return title(r.target).replace(/\s*=\s*/g, ' ');
    case 'advancement': return `Advancement ${title(namesp(r.requiredAdvancement))}`;
    case 'defeat': return `Defeat ${r.amount}x ${r.target}`;
    case 'negate': return `Not (${req(r.inner)})`;
    default: return title(r.variant);
  }
}
function evoText(e) {
  const parts = [];
  if (e.variant === 'item_interact') parts.push(`Use ${itemName(namesp(e.requiredContext || ''))}`);
  else if (e.variant === 'trade') parts.push('Trade');
  for (const r of e.requirements || []) parts.push(req(r));
  if (!parts.length) parts.push(title(e.variant));
  return `${spName(norm(String(e.result).split(' ')[0]))} (${parts.join(', ')})`;
}

// ---- pokedex.csv ----
const pokedexRows = [[
  'National Dex', 'Pokémon Name', 'Type', 'HP', 'Attack', 'Defense', 'Special Attack', 'Special Defense', 'Speed', 'Total',
  'Type I', 'Type II', 'Ability I', 'Ability II', 'Hidden Ability', 'Height (ft)', 'Height (m)', 'Weight (lbs)', 'Weight (kg)',
  'HP EVs', 'Attcak EVs', 'Defense EVs', 'Special Attack EVs', 'Special Defense EVs', 'Speed EVs',
  'Experience Value', 'Catch Rate', 'Experience Growth', 'Base Friendship', 'Pokedex Color', 'Gender Ratio', 'Egg Cycles',
  'Egg Group I', 'Egg Group II', 'Evolution Method',
]];
let noColor = 0;
for (const s of species) {
  const b = s.baseStats, ev = s.evYield;
  const total = b.hp + b.attack + b.defence + b.special_attack + b.special_defence + b.speed;
  const ab = (s.abilities || []);
  const normal = ab.filter((a) => !a.startsWith('h:')).map(abName);
  const hidden = ab.filter((a) => a.startsWith('h:')).map((a) => abName(a.slice(2)));
  const m = s.height / 10, kg = s.weight / 10;
  const types = [cap(s.primaryType), s.secondaryType ? cap(s.secondaryType) : ''];
  const color = Pokedex[norm(s._id)]?.color || '';
  if (!color) noColor++;
  const g = s.maleRatio < 0 ? 'Genderless' : `${+(s.maleRatio * 100).toFixed(1)}% M / ${+((1 - s.maleRatio) * 100).toFixed(1)}% F`;
  pokedexRows.push([
    s.nationalPokedexNumber, spName(s._id), types.filter(Boolean).join('/'),
    b.hp, b.attack, b.defence, b.special_attack, b.special_defence, b.speed, total,
    types[0], types[1], normal[0] || '', normal[1] || '', hidden[0] || '',
    (m * 3.28084).toFixed(1), m.toFixed(1), (kg * 2.20462).toFixed(1), kg.toFixed(1),
    ev.hp, ev.attack, ev.defence, ev.special_attack, ev.special_defence, ev.speed,
    s.baseExperienceYield, s.catchRate, title(s.experienceGroup), s.baseFriendship, color, g, s.eggCycles,
    cap(s.eggGroups?.[0] || ''), cap(s.eggGroups?.[1] || ''),
    (s.evolutions || []).map(evoText).join('; '),
  ]);
}
write('pokedex.csv', pokedexRows);

// ---- pokemon-attacks.csv ----
const learnt = new Set();
const atkRows = [['dex', 'pokemon', 'form', 'move_id', 'move', 'method', 'level', 'implemented']];
const moveName = (id) => lang[`cobblemon.move.${id}`] || Moves[id]?.name || title(id);
const emit = (s, form, moves) => {
  for (const mv of moves || []) {
    const i = mv.indexOf(':');
    const pre = mv.slice(0, i), id = mv.slice(i + 1);
    learnt.add(id);
    const numeric = /^\d+$/.test(pre);
    atkRows.push([s.nationalPokedexNumber, spName(s._id), form, id, moveName(id), numeric ? 'level' : pre, numeric ? pre : '', s.implemented === true]);
  }
};
for (const s of species) {
  emit(s, '', s.moves);
  for (const f of s.forms || []) if (f.moves?.length) emit(s, f.name, f.moves);
}
write('pokemon-attacks.csv', atkRows);

// ---- attack.csv ----
const ids = new Set(learnt);
for (const k of Object.keys(lang)) {
  const m = k.match(/^cobblemon\.move\.([a-z0-9]+)$/);
  if (m && m[1] !== 'category') ids.add(m[1]);
}
const mvRows = [['move_id', 'name', 'type', 'category', 'power', 'accuracy', 'pp', 'priority', 'target', 'description']];
let noMoveDesc = 0, noMoveStat = 0;
for (const id of [...ids].sort()) {
  const m = Moves[id];
  if (!m) noMoveStat++;
  const desc = clean(lang[`cobblemon.move.${id}.desc`] || MovesText[id]?.desc || MovesText[id]?.shortDesc);
  if (!desc) noMoveDesc++;
  mvRows.push([id, moveName(id), m?.type || '', m?.category || '', m ? m.basePower : '',
    m ? (m.accuracy === true ? '—' : m.accuracy) : '', m?.pp ?? '', m?.priority ?? '', m?.target || '', desc]);
}
write('attack.csv', mvRows);
console.log(`  moves without stats: ${noMoveStat}, without description: ${noMoveDesc}`);

// ---- item.csv ----
const evoUse = {}; // item id -> Set("Source -> Result")
const addEvo = (s, e) => {
  const items = [];
  if (e.variant === 'item_interact') items.push(namesp(e.requiredContext || ''));
  for (const r of e.requirements || []) if (r.variant === 'held_item') items.push(namesp(r.itemCondition));
  for (const it of items) (evoUse[it] ??= new Set()).add(`${spName(s._id)} -> ${spName(norm(String(e.result).split(' ')[0]))}`);
};
for (const s of species) {
  (s.evolutions || []).forEach((e) => addEvo(s, e));
  for (const f of s.forms || []) (f.evolutions || []).forEach((e) => addEvo(s, e));
}
const itemRows = [['item_id', 'name', 'category', 'icon', 'description']];
const iconRoot = path.join(root, 'icons', 'items');
let noItemDesc = 0;
for (const cat of fs.readdirSync(iconRoot).sort()) {
  const d = path.join(iconRoot, cat);
  if (!fs.statSync(d).isDirectory()) continue;
  for (const f of fs.readdirSync(d).sort()) {
    if (!f.endsWith('.png')) continue;
    const id = f.slice(0, -4);
    const multi = [1, 2, 3, 4].map((n) => lang[`item.cobblemon.${id}.tooltip_${n}`]).filter(Boolean).join('. ');
    let desc = clean(lang[`item.cobblemon.${id}.tooltip`] || multi ||ItemsText[norm(id)]?.shortDesc || ItemsText[norm(id)]?.desc);
    if (evoUse[id]) desc = (desc ? desc + '. ' : '') + 'Evolves: ' + [...evoUse[id]].sort().join(', ');
    if (!desc) noItemDesc++;
    itemRows.push([id, lang[`item.cobblemon.${id}`] || ItemsText[norm(id)]?.name || title(id), cat, `icons/items/${cat}/${f}`, desc]);
  }
}
write('item.csv', itemRows);
console.log(`  items without description: ${noItemDesc}; species without color: ${noColor}`);

// ---- tm.csv ----
// Cobblemon ships TM definitions (data/cobblemon/tms/<move>.json: move, type, how it is obtained) for some moves.
// Every move that any species learns by TM needs a TM item, so the rest are generated; both kinds are listed here.
const tmDir = path.join(root, 'data', 'cobblemon', 'tms');
const cobblemonTms = new Map();
if (fs.existsSync(tmDir)) for (const f of fs.readdirSync(tmDir).filter((x) => x.endsWith('.json'))) { const j = JSON.parse(fs.readFileSync(path.join(tmDir, f), 'utf8')); cobblemonTms.set(j.moveName, j); }
const learnedByTm = new Set(atkRows.slice(1).filter((r) => r[5] === 'tm').map((r) => r[3]));
const tmRows = [['move_id', 'name', 'type', 'source', 'obtain', 'icon']];
let tmOnlyCobblemon = 0;
for (const id of [...new Set([...cobblemonTms.keys(), ...learnedByTm])].sort()) {
  const c = cobblemonTms.get(id);
  if (c && !learnedByTm.has(id)) tmOnlyCobblemon++;
  const type = c?.type || String(Moves[id]?.type || '').toLowerCase();
  tmRows.push([id, moveName(id), type, c ? 'cobblemon' : 'generated', c ? String(c.obtainMethods?.[0]?.variant || '').replace(/^cobblemon:/, '') : '', type ? `icons/tm/${type}.png` : '']);
}
write('tm.csv', tmRows);
console.log(`  TMs: ${cobblemonTms.size} defined by Cobblemon, ${learnedByTm.size} moves learned by TM, ${tmOnlyCobblemon} Cobblemon TMs for moves no species learns by TM`);
