// Loads the Pokemon Showdown data that ships inside the Cobblemon jar (data/cobblemon/showdown.zip, MIT licence).
// The files are compiled CommonJS modules, so they are written to a folder and require()d.
//   stageShowdown(jarPath, dir)  -> writes data_moves.js, data_text_moves.js ... into dir (the layout build-csv.mjs expects)
//   loadShowdown(jarPath, dir)   -> { Moves, Abilities, Items, Conditions, Pokedex, MovesText, AbilitiesText, ItemsText }
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { openZip } from './zip.mjs';

export function stageShowdown(jarPath, dir, files) {
  const jar = openZip(jarPath);
  const sd = openZip(jar.read('data/cobblemon/showdown.zip'));
  fs.mkdirSync(dir, { recursive: true });
  const written = [];
  for (const f of files) {
    if (!sd.has(f)) throw new Error('showdown.zip has no ' + f);
    const out = path.join(dir, f.replace(/\//g, '_'));
    fs.writeFileSync(out, sd.read(f));
    written.push(out);
  }
  return written;
}

export function loadShowdown(jarPath, dir, files) {
  stageShowdown(jarPath, dir, files);
  const require = createRequire(import.meta.url);
  const first = (f) => { const m = require(path.resolve(dir, f.replace(/\//g, '_'))); return m[Object.keys(m)[0]]; };
  return {
    Moves: first('data/moves.js'), Abilities: first('data/abilities.js'), Items: first('data/items.js'),
    Conditions: first('data/conditions.js'), Pokedex: first('data/pokedex.js'),
    MovesText: first('data/text/moves.js'), AbilitiesText: first('data/text/abilities.js'), ItemsText: first('data/text/items.js'),
  };
}
