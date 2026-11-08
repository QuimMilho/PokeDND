// Tiny CSV reader: handles a UTF-8 byte-order mark, quoted fields, "" escapes and CRLF/LF line ends.
//   readCsv(path) -> { header: string[], rows: string[][] }      (blank lines are skipped)
//   readCsvObjects(path) -> object[]                             (keys from the header)
import fs from 'node:fs';

export function parseCsv(text) {
  if (text.charCodeAt(0) === 0xfeff) text = text.slice(1);
  const rows = []; let row = [], field = '', q = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (q) {
      if (c === '"') { if (text[i + 1] === '"') { field += '"'; i++; } else q = false; }
      else field += c;
    } else if (c === '"') q = true;
    else if (c === ',') { row.push(field); field = ''; }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(field); field = '';
      if (!(row.length === 1 && row[0] === '')) rows.push(row);
      row = [];
    } else field += c;
  }
  if (field !== '' || row.length) { row.push(field); rows.push(row); }
  return rows;
}

export function readCsv(file) {
  const all = parseCsv(fs.readFileSync(file, 'utf8'));
  return { header: all[0], rows: all.slice(1) };
}

export function readCsvObjects(file) {
  const { header, rows } = readCsv(file);
  return rows.map((r) => Object.fromEntries(header.map((h, i) => [h, r[i] ?? ''])));
}
