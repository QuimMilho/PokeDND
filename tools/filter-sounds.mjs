import fs from 'node:fs';
const [,, file] = process.argv;
const s = JSON.parse(fs.readFileSync(file, 'utf8'));
const out = {};
for (const [k, v] of Object.entries(s)) if (k.startsWith('pokemon.')) out[k] = v;
fs.writeFileSync(file, JSON.stringify(out));
console.log('kept', Object.keys(out).length, 'of', Object.keys(s).length);
