// Zero-dependency zip/jar reader (central directory + node:zlib).
// Usage:
//   import { openZip } from './lib/zip.mjs';
//   const z = openZip('Cobblemon.jar');            // path or Buffer
//   z.entries                                      // [{ name, size, csize, method, offset, dir }]
//   z.has('fabric.mod.json'); z.read('fabric.mod.json') // -> Buffer
//   const inner = openZip(z.read('data/cobblemon/showdown.zip'));  // nested zips work too
import fs from 'node:fs';
import zlib from 'node:zlib';

const U32 = 0xffffffff;

export function openZip(source) {
  const buf = Buffer.isBuffer(source) ? source : fs.readFileSync(source);

  // ---- end of central directory (search backwards; the comment may be up to 64 KiB) ----
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 22 - 0xffff); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('not a zip file (no end-of-central-directory record)');
  let count = buf.readUInt16LE(eocd + 10);
  let cdOff = buf.readUInt32LE(eocd + 16);

  // ---- zip64 ----
  if ((count === 0xffff || cdOff === U32) && eocd >= 20 && buf.readUInt32LE(eocd - 20) === 0x07064b50) {
    const rec = Number(buf.readBigUInt64LE(eocd - 20 + 8));
    if (buf.readUInt32LE(rec) === 0x06064b50) {
      count = Number(buf.readBigUInt64LE(rec + 32));
      cdOff = Number(buf.readBigUInt64LE(rec + 48));
    }
  }

  // ---- central directory ----
  const entries = [];
  const byName = new Map();
  let p = cdOff;
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error('corrupt central directory at entry ' + n);
    const flags = buf.readUInt16LE(p + 8);
    const method = buf.readUInt16LE(p + 10);
    let csize = buf.readUInt32LE(p + 20);
    let size = buf.readUInt32LE(p + 24);
    const nameLen = buf.readUInt16LE(p + 28), extraLen = buf.readUInt16LE(p + 30), commentLen = buf.readUInt16LE(p + 32);
    let offset = buf.readUInt32LE(p + 42);
    const name = buf.toString(flags & 0x800 ? 'utf8' : 'latin1', p + 46, p + 46 + nameLen);

    if (size === U32 || csize === U32 || offset === U32) { // zip64 extra field (id 0x0001)
      let q = p + 46 + nameLen; const end = q + extraLen;
      while (q + 4 <= end) {
        const id = buf.readUInt16LE(q), len = buf.readUInt16LE(q + 2);
        if (id === 1) {
          let r = q + 4;
          if (size === U32) { size = Number(buf.readBigUInt64LE(r)); r += 8; }
          if (csize === U32) { csize = Number(buf.readBigUInt64LE(r)); r += 8; }
          if (offset === U32) { offset = Number(buf.readBigUInt64LE(r)); r += 8; }
        }
        q += 4 + len;
      }
    }
    const e = { name, size, csize, method, offset, dir: name.endsWith('/') };
    entries.push(e); byName.set(name, e);
    p += 46 + nameLen + extraLen + commentLen;
  }

  function read(nameOrEntry) {
    const e = typeof nameOrEntry === 'string' ? byName.get(nameOrEntry) : nameOrEntry;
    if (!e) throw new Error('no such entry: ' + nameOrEntry);
    if (buf.readUInt32LE(e.offset) !== 0x04034b50) throw new Error('corrupt local header for ' + e.name);
    const start = e.offset + 30 + buf.readUInt16LE(e.offset + 26) + buf.readUInt16LE(e.offset + 28);
    const raw = buf.subarray(start, start + e.csize);
    if (e.method === 0) return Buffer.from(raw);
    if (e.method === 8) return zlib.inflateRawSync(raw);
    throw new Error(`unsupported compression method ${e.method} for ${e.name}`);
  }

  return {
    entries,
    has: (name) => byName.has(name),
    entry: (name) => byName.get(name),
    read,
    text: (name) => read(name).toString('utf8'),
    /** entries (files only) whose name starts with `prefix` */
    under: (prefix) => entries.filter((e) => !e.dir && e.name.startsWith(prefix)),
  };
}
