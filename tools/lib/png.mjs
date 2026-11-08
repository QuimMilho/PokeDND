// Minimal PNG decoder/encoder (8-bit and palette PNGs, non-interlaced) - zero dependencies.
// Needs Node >= 22.2 (zlib.crc32).
import zlib from 'node:zlib';

export function crc32(buf) { return zlib.crc32(buf) >>> 0; }
export function decodePng(buf) {
  let p = 8, w, h, depth, ctype, interlace, plte = null, trns = null; const idat = [];
  while (p < buf.length) {
    const len = buf.readUInt32BE(p), type = buf.toString('latin1', p + 4, p + 8), d = buf.subarray(p + 8, p + 8 + len);
    if (type === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); depth = d[8]; ctype = d[9]; interlace = d[12]; }
    else if (type === 'PLTE') plte = d; else if (type === 'tRNS') trns = d; else if (type === 'IDAT') idat.push(d);
    p += 12 + len;
  }
  if (interlace) throw new Error('interlaced png');
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const ch = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ctype], bpp = Math.max(1, ch * depth / 8 | 0), stride = Math.ceil(w * ch * depth / 8);
  const px = Buffer.alloc(h * stride); let prev = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const ft = raw[y * (stride + 1)], line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)), cur = px.subarray(y * stride, (y + 1) * stride);
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? cur[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0; let v = line[i];
      if (ft === 1) v += a; else if (ft === 2) v += b; else if (ft === 3) v += (a + b) >> 1;
      else if (ft === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c); }
      cur[i] = v & 255;
    }
    prev = cur;
  }
  const out = new Uint8Array(w * h * 4);
  const sample = (y, idx) => { // idx-th sample in row, returns raw value
    const row = y * stride;
    if (depth === 8) return px[row + idx]; if (depth === 16) return px[row + idx * 2];
    const bit = idx * depth, byte = px[row + (bit >> 3)]; return (byte >> (8 - depth - (bit & 7))) & ((1 << depth) - 1);
  };
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const o = (y * w + x) * 4; let r, g, b, a = 255;
    if (ctype === 6) { r = sample(y, x * 4); g = sample(y, x * 4 + 1); b = sample(y, x * 4 + 2); a = sample(y, x * 4 + 3); }
    else if (ctype === 2) { r = sample(y, x * 3); g = sample(y, x * 3 + 1); b = sample(y, x * 3 + 2); }
    else if (ctype === 0) { r = g = b = sample(y, x); if (depth < 8) r = g = b = r * 255 / ((1 << depth) - 1); }
    else if (ctype === 4) { r = g = b = sample(y, x * 2); a = sample(y, x * 2 + 1); }
    else { const i = sample(y, x); r = plte[i * 3]; g = plte[i * 3 + 1]; b = plte[i * 3 + 2]; a = trns && i < trns.length ? trns[i] : 255; }
    out[o] = r; out[o + 1] = g; out[o + 2] = b; out[o + 3] = a;
  }
  return { w, h, data: out };
}
export function encodePng(w, h, rgba) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; Buffer.from(rgba.buffer, rgba.byteOffset + y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1); }
  const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td)); return Buffer.concat([l, td, c]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
