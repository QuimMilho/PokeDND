// Generates the settings registry of the API from Appendix A of project_v2.md (phase 33).
// Zero dependencies.
//
// Usage: node tools/gen-settings.mjs [--check]
//
//   reads   project_v2.md                      Appendix A: every key, default, description and chapter
//           tools/settings-schema.json         the kind of value, allowed values and limits (not in Appendix A)
//   writes  API/src/main/resources/settings/registry.json   (committed, so the API needs no tool at build time)
//
// --check writes nothing and fails when the committed registry is not what the document would give now.
// The generator also fails when settings-schema.json names a key that Appendix A does not have, so the two cannot drift.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const specFile = path.join(repo, 'project_v2.md');
const schemaFile = path.join(repo, 'tools', 'settings-schema.json');
const outFile = path.join(repo, 'API', 'src', 'main', 'resources', 'settings', 'registry.json');

const spec = fs.readFileSync(specFile, 'utf8').replace(/\r\n/g, '\n');
const start = spec.indexOf('### Appendix A');
const end = spec.indexOf('### Appendix B');
if (start < 0 || end < 0) throw new Error('Appendix A not found in project_v2.md');
const schema = JSON.parse(fs.readFileSync(schemaFile, 'utf8')).settings;

/** The text inside the first `...` of a cell, or the cell without its asterisks. */
const codeOf = (cell) => {
  const m = /^`([^`]*)`$/.exec(cell.trim());
  return m ? m[1] : null;
};

const entries = [];
let group = '';
for (const line of spec.slice(start, end).split('\n')) {
  const h = /^#### (.+)$/.exec(line);
  if (h) {
    group = h[1].trim();
    continue;
  }
  if (!line.startsWith('| `')) continue;
  const cells = line.split(/(?<!\\)\|/).slice(1, -1).map((c) => c.trim());
  if (cells.length !== 4) throw new Error('A row of Appendix A does not have four cells: ' + line);
  const [keyCell, defaultCell, description, chapterCell] = cells;
  // "`a` / `b`" in both the key and the default cell: two settings in one row
  const keys = keyCell.split(' / ').map((k) => codeOf(k) ?? (() => { throw new Error('Bad key cell: ' + keyCell); })());
  const italic = /^\*\((.*)\)\*$/.exec(defaultCell);
  let defaults;
  if (keys.length > 1) {
    defaults = defaultCell.split(' / ').map((d) => codeOf(d));
    if (defaults.length !== keys.length || defaults.includes(null)) throw new Error('Bad default cell for ' + keyCell);
  } else if (italic) {
    defaults = [null];
  } else {
    const c = codeOf(defaultCell);
    if (c === null) throw new Error('Bad default cell for ' + keyCell + ': ' + defaultCell);
    defaults = [c];
  }
  keys.forEach((key, i) => entries.push({ key, group, defaultText: italic ? defaultCell : defaults[i], italic: !!italic && keys.length === 1, description, chapter: Number(chapterCell) }));
}

const seen = new Set();
for (const e of entries) {
  if (seen.has(e.key)) throw new Error('Appendix A lists ' + e.key + ' twice');
  seen.add(e.key);
}
for (const k of Object.keys(schema)) {
  if (!seen.has(k)) throw new Error('tools/settings-schema.json has a key that Appendix A does not have: ' + k);
}

const settings = entries.map((e) => {
  const s = schema[e.key] ?? {};
  let type = s.type;
  let value;
  if (e.italic) {
    type = type ?? 'data';
    value = null;
  } else if (!type) {
    if (e.defaultText === 'true' || e.defaultText === 'false') type = 'bool';
    else if (/^-?\d+$/.test(e.defaultText)) type = 'int';
    else if (/^-?\d+\.\d+$/.test(e.defaultText)) type = 'decimal';
    else if (e.defaultText === '{}') type = 'json';
    else type = s.allowed ? 'enum' : 'string';
  }
  if (!e.italic) {
    switch (type) {
      case 'bool': value = e.defaultText === 'true'; break;
      case 'int': value = parseInt(e.defaultText, 10); break;
      case 'decimal': value = parseFloat(e.defaultText); break;
      case 'json': value = JSON.parse(e.defaultText); break;
      case 'intList': value = e.defaultText.split(',').map((t) => parseInt(t.trim(), 10)); break;
      case 'csv': value = e.defaultText.split(',').map((t) => t.trim()).join(','); break;
      case 'data': value = e.defaultText === 'official' ? 'official' : null; break;
      default: value = e.defaultText; // string, enum
    }
  } else if (type !== 'data') {
    throw new Error(e.key + ' has no value in Appendix A but is not a data setting');
  }
  const out = { key: e.key, group: e.group, type, default: value, defaultText: e.defaultText, chapter: e.chapter, description: e.description };
  for (const f of ['allowed', 'min', 'max', 'pattern', 'patternHint', 'tokenPattern', 'tokenHint', 'minItems', 'maxItems', 'itemMin', 'itemMax', 'unique', 'minLength', 'maxLength']) {
    if (s[f] !== undefined) out[f] = s[f];
  }
  return out;
});

const json = JSON.stringify({ source: 'project_v2.md, Appendix A', count: settings.length, settings }, null, 1) + '\n';
if (process.argv.includes('--check')) {
  const have = fs.existsSync(outFile) ? fs.readFileSync(outFile, 'utf8') : '';
  if (have !== json) {
    console.error('FAIL  API/src/main/resources/settings/registry.json is out of date: run node tools/gen-settings.mjs');
    process.exit(1);
  }
  console.log(`PASS  the registry matches Appendix A (${settings.length} settings)`);
} else {
  fs.mkdirSync(path.dirname(outFile), { recursive: true });
  fs.writeFileSync(outFile, json);
  console.log(`Wrote ${path.relative(repo, outFile)}: ${settings.length} settings`);
}
