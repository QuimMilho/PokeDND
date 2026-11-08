// Zero-dependency software renderer: Cobblemon Bedrock .geo.json + texture -> 2D PNG (icons and card art).
// Usage: node render-icons.mjs <extractedRoot> <outDir> [onlyFolderSubstring] [options]
// Options:
//   --size <px>        output size, square. Default 128 (icons). Card art: 1024.
//   --ss <n>           supersampling factor. Default 2.
//   --only <text>      only folders whose name contains <text> (same as the 3rd positional argument).
//   --dex <a>-<b>      only species with a Pokedex number in that range, e.g. --dex 1-151.
//   --no-shiny         skip shiny variations.
//   --yaw <deg>        camera yaw. Default -35.
//   --pitch <deg>      camera pitch. Default 15.
//   --margin <0-0.4>   empty border around the model, as a fraction of the size. Default 0.06.
// Output: <outDir>/<dex>_<slug>/<variant>.png - the same layout for icons and for card art.
// The model is always fitted to its own bounding box, so no per-species framing values are needed.
import fs from 'node:fs';
import path from 'node:path';
import { decodePng, encodePng } from './lib/png.mjs';

const args = process.argv.slice(2);
const opt = {}, pos = [];
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--no-shiny') opt.noShiny = true;
  else if (args[i].startsWith('--')) opt[args[i].slice(2)] = args[++i];
  else pos.push(args[i]);
}
const [ROOT, OUT, ONLY_POS] = pos;
if (!ROOT || !OUT) { console.error('usage: node render-icons.mjs <extractedRoot> <outDir> [onlyFolderSubstring] [options]'); process.exit(1); }
const ONLY = opt.only ?? ONLY_POS;
const POKE = path.join(ROOT, 'assets/cobblemon/bedrock/pokemon');
const TEX = path.join(ROOT, 'assets/cobblemon/textures');
const SIZE = +(opt.size ?? 128), SS = +(opt.ss ?? 2), W = SIZE * SS;
const YAW = +(opt.yaw ?? -35) * Math.PI / 180, PITCH = +(opt.pitch ?? 15) * Math.PI / 180;
const MARGIN = +(opt.margin ?? 0.06);
const DEX = opt.dex ? opt.dex.split('-').map(Number) : null; // [from, to]
// Big buffers are allocated once and reused for every render.
const BUF = new Float32Array(W * W * 4), ZB = new Float32Array(W * W);

const texCache = new Map();
function loadTex(id) { // "cobblemon:textures/pokemon/x.png"
  const rel = id.replace(/^[^:]+:/, '');
  if (!texCache.has(rel)) { if (texCache.size > 300) texCache.clear(); texCache.set(rel, decodePng(fs.readFileSync(path.join(ROOT, 'assets/cobblemon', rel)))); }
  return texCache.get(rel);
}

// ---------- matrices (row-major 3x4 affine as 12-array) ----------
const I = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0];
function mul(a, b) { // a*b
  const r = new Array(12);
  for (let i = 0; i < 3; i++) for (let j = 0; j < 4; j++)
    r[i * 4 + j] = a[i * 4] * b[j] + a[i * 4 + 1] * b[4 + j] + a[i * 4 + 2] * b[8 + j] + (j === 3 ? a[i * 4 + 3] : 0);
  return r;
}
function rotAbout(rx, ry, rz, pivot) { // R = Rz*Ry*Rx about pivot, degrees
  const [x, y, z] = [rx, ry, rz].map(d => d * Math.PI / 180);
  const cx = Math.cos(x), sx = Math.sin(x), cy = Math.cos(y), sy = Math.sin(y), cz = Math.cos(z), sz = Math.sin(z);
  const Rx = [1, 0, 0, 0, 0, cx, -sx, 0, 0, sx, cx, 0], Ry = [cy, 0, sy, 0, 0, 1, 0, 0, -sy, 0, cy, 0], Rz = [cz, -sz, 0, 0, sz, cz, 0, 0, 0, 0, 1, 0];
  const R = mul(Rz, mul(Ry, Rx));
  R[3] = pivot[0] - (R[0] * pivot[0] + R[1] * pivot[1] + R[2] * pivot[2]);
  R[7] = pivot[1] - (R[4] * pivot[0] + R[5] * pivot[1] + R[6] * pivot[2]);
  R[11] = pivot[2] - (R[8] * pivot[0] + R[9] * pivot[1] + R[10] * pivot[2]);
  return R;
}
function scaleAbout(sc, pivot) { return [sc[0], 0, 0, pivot[0] * (1 - sc[0]), 0, sc[1], 0, pivot[1] * (1 - sc[1]), 0, 0, sc[2], pivot[2] * (1 - sc[2])]; }
const apply = (m, p) => [m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3], m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7], m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11]];
const applyN = (m, p) => [m[0] * p[0] + m[1] * p[1] + m[2] * p[2], m[4] * p[0] + m[5] * p[1] + m[6] * p[2], m[8] * p[0] + m[9] * p[1] + m[10] * p[2]];

// ---------- geometry -> quads (world space) ----------
const FACES = { // corner order gives uv corners: [TL, TR, BR, BL] as seen from outside
  north: { n: [0, 0, -1], c: [[1, 1, 0], [0, 1, 0], [0, 0, 0], [1, 0, 0]] },
  south: { n: [0, 0, 1], c: [[0, 1, 1], [1, 1, 1], [1, 0, 1], [0, 0, 1]] },
  east: { n: [1, 0, 0], c: [[1, 1, 1], [1, 1, 0], [1, 0, 0], [1, 0, 1]] },
  west: { n: [-1, 0, 0], c: [[0, 1, 0], [0, 1, 1], [0, 0, 1], [0, 0, 0]] },
  up: { n: [0, 1, 0], c: [[0, 1, 0], [1, 1, 0], [1, 1, 1], [0, 1, 1]] },
  down: { n: [0, -1, 0], c: [[0, 0, 1], [1, 0, 1], [1, 0, 0], [0, 0, 0]] },
};
function buildQuads(geo, anim = {}) {
  const desc = geo.description || {}, TW = desc.texture_width || 64, TH = desc.texture_height || 64;
  const bones = new Map(); for (const b of geo.bones || []) bones.set(b.name, b);
  const mats = new Map();
  const bm = (b) => {
    if (mats.has(b.name)) return mats.get(b.name);
    mats.set(b.name, I()); // cycle guard
    const parent = b.parent && bones.get(b.parent) ? bm(bones.get(b.parent)) : I();
    const a = anim[b.name] || {}, r0 = b.rotation || [0, 0, 0], ar = a.rotation || [0, 0, 0], r = [r0[0] + ar[0], r0[1] + ar[1], r0[2] + ar[2]];
    const pv = b.pivot || [0, 0, 0], ap = a.position || [0, 0, 0], sc = a.scale || [1, 1, 1];
    let local = mul(rotAbout(-r[0], -r[1], r[2], pv), scaleAbout(sc, pv));
    local = mul([1, 0, 0, -ap[0], 0, 1, 0, ap[1], 0, 0, 1, ap[2]], local);
    const m = mul(parent, local);
    mats.set(b.name, m); return m;
  };
  const quads = [];
  for (const b of geo.bones || []) {
    const M = bm(b);
    for (const c of b.cubes || []) {
      const [ox, oy, oz] = c.origin, [sx, sy, sz] = c.size, inf = c.inflate ?? b.inflate ?? 0;
      if (!sx && !sy && !sz) continue;
      const o = [ox - inf, oy - inf, oz - inf], s = [sx + 2 * inf, sy + 2 * inf, sz + 2 * inf];
      let CM = M;
      if (c.rotation) { const r = c.rotation; CM = mul(M, rotAbout(-r[0], -r[1], r[2], c.pivot || [0, 0, 0])); }
      const mirror = c.mirror ?? b.mirror ?? false;
      const uvs = {};
      if (Array.isArray(c.uv)) {
        const [u, v] = c.uv;
        uvs.north = [u + sz, v + sz, sx, sy]; uvs.east = [u, v + sz, sz, sy]; uvs.south = [u + sz + sx + sz, v + sz, sx, sy];
        uvs.west = [u + sz + sx, v + sz, sz, sy]; uvs.up = [u + sz, v, sx, sz]; uvs.down = [u + sz + sx, v + sz, sx, -sz];
        if (mirror) { [uvs.east, uvs.west] = [uvs.west, uvs.east]; }
      } else if (c.uv) {
        for (const [f, d] of Object.entries(c.uv)) if (d && d.uv) uvs[f] = [d.uv[0], d.uv[1], d.uv_size?.[0] ?? 0, d.uv_size?.[1] ?? 0];
      }
      for (const [f, fd] of Object.entries(FACES)) {
        const uv = uvs[f]; if (!uv) continue;
        let [u0, v0, uw, vh] = uv; const flipU = mirror && Array.isArray(c.uv);
        const corners = fd.c.map(k => apply(CM, [o[0] + k[0] * s[0], o[1] + k[1] * s[1], o[2] + k[2] * s[2]]));
        // uv at corners TL,TR,BR,BL
        let uA = u0, uB = u0 + uw; if (flipU) [uA, uB] = [uB, uA];
        const t = [[uA, v0], [uB, v0], [uB, v0 + vh], [uA, v0 + vh]].map(([u, v]) => [u / TW, v / TH]);
        quads.push({ p: corners, t, n: applyN(CM, fd.n) });
      }
    }
  }
  return quads;
}

// ---------- rasterize ----------
function render(quads, layers) {
  const cy = Math.cos(YAW), sy = Math.sin(YAW), cp = Math.cos(PITCH), sp = Math.sin(PITCH);
  const view = (p) => { // yaw about Y, pitch about X; screen x = -X (camera at -Z looking +Z)
    const x1 = p[0] * cy + p[2] * sy, z1 = -p[0] * sy + p[2] * cy;
    const y2 = p[1] * cp - z1 * sp, z2 = p[1] * sp + z1 * cp; return [-x1, y2, z2];
  };
  const vq = quads.map(q => ({ p: q.p.map(view), t: q.t, n: view(q.n) }));
  let minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
  for (const q of vq) for (const p of q.p) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]); }
  if (!(maxX > minX)) throw new Error('empty model');
  const margin = MARGIN * W, sc = (W - 2 * margin) / Math.max(maxX - minX, maxY - minY);
  const offX = (W - (maxX - minX) * sc) / 2 - minX * sc, offY = (W - (maxY - minY) * sc) / 2 + maxY * sc;
  BUF.fill(0); ZB.fill(1e9); const buf = BUF, zb = ZB;
  const light = [-0.3, 0.8, -0.5], ll = Math.hypot(...light);
  for (const q of vq) {
    const nl = Math.hypot(...q.n) || 1, shade = 0.62 + 0.38 * Math.max(0, (q.n[0] * light[0] + q.n[1] * light[1] + q.n[2] * light[2]) / (nl * ll));
    const sp_ = q.p.map(p => [p[0] * sc + offX, offY - p[1] * sc, p[2]]);
    for (const [a, b, c] of [[0, 1, 2], [0, 2, 3]]) {
      const A = sp_[a], B = sp_[b], C = sp_[c], tA = q.t[a], tB = q.t[b], tC = q.t[c];
      const det = (B[1] - C[1]) * (A[0] - C[0]) + (C[0] - B[0]) * (A[1] - C[1]); if (Math.abs(det) < 1e-9) continue;
      const x0 = Math.max(0, Math.floor(Math.min(A[0], B[0], C[0]))), x1 = Math.min(W - 1, Math.ceil(Math.max(A[0], B[0], C[0])));
      const y0 = Math.max(0, Math.floor(Math.min(A[1], B[1], C[1]))), y1 = Math.min(W - 1, Math.ceil(Math.max(A[1], B[1], C[1])));
      for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
        const px = x + 0.5, py = y + 0.5;
        const l1 = ((B[1] - C[1]) * (px - C[0]) + (C[0] - B[0]) * (py - C[1])) / det, l2 = ((C[1] - A[1]) * (px - C[0]) + (A[0] - C[0]) * (py - C[1])) / det, l3 = 1 - l1 - l2;
        if (l1 < -1e-4 || l2 < -1e-4 || l3 < -1e-4) continue;
        const z = l1 * A[2] + l2 * B[2] + l3 * C[2]; if (z >= zb[y * W + x]) continue;
        const u = l1 * tA[0] + l2 * tB[0] + l3 * tC[0], v = l1 * tA[1] + l2 * tB[1] + l3 * tC[1];
        let r = 0, g = 0, bl = 0, al = 0;
        for (const L of layers) {
          const tx = Math.min(L.w - 1, Math.max(0, Math.floor(u * L.w))), ty = Math.min(L.h - 1, Math.max(0, Math.floor(v * L.h))), o = (ty * L.w + tx) * 4;
          const la = L.data[o + 3] / 255; if (la <= 0) continue;
          r = r * (1 - la) + L.data[o] * la; g = g * (1 - la) + L.data[o + 1] * la; bl = bl * (1 - la) + L.data[o + 2] * la; al = al + la * (1 - al);
        }
        if (al < 0.5) continue;
        zb[y * W + x] = z; const o = (y * W + x) * 4;
        buf[o] = Math.min(255, r * shade); buf[o + 1] = Math.min(255, g * shade); buf[o + 2] = Math.min(255, bl * shade); buf[o + 3] = 255;
      }
    }
  }
  const out = new Uint8Array(SIZE * SIZE * 4);
  for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
    let r = 0, g = 0, b = 0, a = 0;
    for (let j = 0; j < SS; j++) for (let i = 0; i < SS; i++) { const o = ((y * SS + j) * W + x * SS + i) * 4, al = buf[o + 3] / 255; r += buf[o] * al; g += buf[o + 1] * al; b += buf[o + 2] * al; a += al; }
    const o = (y * SIZE + x) * 4; if (a > 0) { out[o] = r / a; out[o + 1] = g / a; out[o + 2] = b / a; out[o + 3] = 255 * a / (SS * SS); }
  }
  return out;
}

// ---------- model index & driver ----------
const modelIndex = new Map(); // name -> [paths]
(function idx(d) { for (const e of fs.readdirSync(d, { withFileTypes: true })) { const p = path.join(d, e.name); if (e.isDirectory()) idx(p); else if (e.name.endsWith('.geo.json')) { const k = e.name.slice(0, -9); (modelIndex.get(k) || modelIndex.set(k, []).get(k)).push(p); } } })(path.join(POKE, 'models'));

// ---------- animation (frame 0) ----------
const index = (sub, ext) => { const m = new Map(); (function w(d) { for (const e of fs.readdirSync(d, { withFileTypes: true })) { const p = path.join(d, e.name); if (e.isDirectory()) w(p); else if (e.name.endsWith(ext)) { const k = e.name.slice(0, -ext.length); (m.get(k) || m.set(k, []).get(k)).push(p); } } })(path.join(POKE, sub)); return m; };
const poserIndex = index('posers', '.json'), animIndex = index('animations', '.animation.json');
const pick = (m, k, folder) => { const c = m.get(k); return c && (c.find(p => p.includes(path.sep + folder + path.sep)) || c[0]); };
const MATH = { sin: d => Math.sin(d * Math.PI / 180), cos: d => Math.cos(d * Math.PI / 180), abs: Math.abs, pow: Math.pow, min: Math.min, max: Math.max, floor: Math.floor, ceil: Math.ceil, sqrt: Math.sqrt, round: Math.round, pi: Math.PI, random: () => 0.5, clamp: (v, a, b) => Math.min(b, Math.max(a, v)), lerp: (a, b, t) => a + (b - a) * t, mod: (a, b) => a % b, exp: Math.exp, ln: Math.log, asin: x => Math.asin(x) * 180 / Math.PI, acos: x => Math.acos(x) * 180 / Math.PI, atan: x => Math.atan(x) * 180 / Math.PI };
function ev(x) {
  if (typeof x === 'number') return x; if (typeof x !== 'string') return 0;
  let e = x.toLowerCase().replace(/\b(q|query|v|variable|c|context|t|temp)\.[a-z_0-9.]+/g, '0').replace(/;\s*$/, '');
  if (!/^[\w\s+\-*/().,<>=!?:&|%]*$/.test(e)) return 0;
  try { const r = Function('math', 'return (' + e + ')')(MATH); return Number.isFinite(r) ? r : 0; } catch { return 0; }
}
function vec(v, dflt) {
  if (v == null) return null;
  if (typeof v === 'number' || typeof v === 'string') { const n = ev(v); return [n, n, n]; }
  if (Array.isArray(v)) return v.slice(0, 3).map(ev);
  if (typeof v === 'object') {
    if (v.vector) return vec(v.vector); const keys = Object.keys(v).filter(k => !isNaN(+k)).sort((a, b) => a - b); if (!keys.length) return null;
    const k = v[keys[0]]; return vec(Array.isArray(k) || typeof k !== 'object' ? k : (k.post ?? k.pre));
  }
  return null;
}
function loadAnim(folder, poserName) {
  const pp = pick(poserIndex, poserName, folder); if (!pp) return {};
  const poser = JSON.parse(fs.readFileSync(pp, 'utf8')), poses = poser.poses || {};
  const byName = poses.portrait || poses.standing || Object.values(poses).find(p => (p.poseTypes || []).includes('STAND') && !p.isBattle) || Object.values(poses)[0]; if (!byName) return {};
  const out = {};
  for (const a of byName.animations || []) {
    if (typeof a !== 'string') continue; const m = /^q\.bedrock\('([^']+)',\s*'([^']+)'/.exec(a); if (!m) continue;
    const ap = pick(animIndex, m[1], folder); if (!ap) continue;
    const j = JSON.parse(fs.readFileSync(ap, 'utf8')); const an = j.animations?.['animation.' + m[1] + '.' + m[2]] || Object.entries(j.animations || {}).find(([k]) => k.endsWith('.' + m[2]))?.[1]; if (!an) continue;
    for (const [bn, bd] of Object.entries(an.bones || {})) {
      const o = out[bn] ||= {}; const r = vec(bd.rotation), p = vec(bd.position), s = vec(bd.scale);
      if (r) o.rotation = (o.rotation || [0, 0, 0]).map((x, i) => x + r[i]);
      if (p) o.position = (o.position || [0, 0, 0]).map((x, i) => x + p[i]);
      if (s) o.scale = (o.scale || [1, 1, 1]).map((x, i) => x * s[i]);
    }
  }
  return out;
}
const errors = []; let count = 0;
const texName = (t) => typeof t === 'string' ? t : (t?.frames?.[0] ?? t?.textures?.[0] ?? null);
const SKIP = (a) => a.some(x => x.startsWith('cosmetic_item') || x === 'alpha_eyes');
const resolverRoot = path.join(POKE, 'resolvers');
const fbCache = new Map();
function folderBase(folder) {
  if (!fbCache.has(folder)) { let b = {}; for (const f of fs.readdirSync(path.join(resolverRoot, folder)).sort()) { try { const v = JSON.parse(fs.readFileSync(path.join(resolverRoot, folder, f), 'utf8')).variations?.find(x => !x.aspects?.length); if (v) { b = v; break; } } catch { } } fbCache.set(folder, b); }
  return fbCache.get(folder);
}
for (const folder of fs.readdirSync(resolverRoot).sort()) {
  if (ONLY && !folder.includes(ONLY)) continue;
  if (DEX) { const n = parseInt(folder, 10); if (!(n >= DEX[0] && n <= DEX[1])) continue; }
  for (const rf of fs.readdirSync(path.join(resolverRoot, folder)).filter(f => f.endsWith('.json'))) {
    let res; try { res = JSON.parse(fs.readFileSync(path.join(resolverRoot, folder, rf), 'utf8')); } catch (e) { errors.push(`${folder}/${rf}: bad json`); continue; }
    const vars = res.variations || [], base = vars.find(v => !v.aspects?.length) || folderBase(folder);
    const stem = rf.replace(/\.json$/, '').replace(/^\d+_/, '').replace(/_base$/, '');
    for (const v of vars) {
      const aspects = v.aspects || []; if (SKIP(aspects)) continue;
      if (opt.noShiny && aspects.includes('shiny')) continue;
      // Cobblemon merges: more specific variations override base fields. Also inherit from any single-aspect variation that is a subset (e.g. female model).
      const model = v.model ?? vars.filter(o => o !== v && o.model && o.aspects?.length && o.aspects.every(a => aspects.includes(a)) && !SKIP(o.aspects)).map(o => o.model)[0] ?? base.model;
      const texture = texName(v.texture) ?? texName(base.texture);
      const layers = (v.layers ?? (v.texture ? [] : base.layers) ?? []).filter(l => l.emissive && texName(l.texture));
      const name = [stem, ...aspects.filter(a => !stem.includes(a))].join('_').replace(/[?]/g, 'question').replace(/[<>:"|*]/g, '_'); const dest = path.join(OUT, folder, name + '.png');
      try {
        if (!model || !texture) throw new Error('missing model/texture');
        const mname = model.replace(/^[^:]+:/, '').replace(/\.geo$/, '');
        const cands = modelIndex.get(mname); if (!cands) throw new Error('model not found ' + mname);
        const mp = cands.find(p => p.includes(path.sep + folder + path.sep)) || cands[0];
        const mj = JSON.parse(fs.readFileSync(mp, 'utf8')); const geo = mj['minecraft:geometry']?.[0]; if (!geo) throw new Error('no geometry');
        let anim = {}; const poserName = (v.poser ?? base.poser)?.replace(/^[^:]+:/, ''); if (poserName) { try { anim = loadAnim(folder, poserName); } catch { } }
        const quads = buildQuads(geo, anim);
        const texs = [loadTex(texture), ...layers.map(l => loadTex(texName(l.texture)))];
        const rgba = render(quads, texs);
        fs.mkdirSync(path.dirname(dest), { recursive: true }); fs.writeFileSync(dest, encodePng(SIZE, SIZE, rgba)); count++;
      } catch (e) { errors.push(`${folder}/${rf} [${aspects.join(',')}]: ${e.message}`); }
    }
  }
}
fs.mkdirSync(OUT, { recursive: true }); fs.writeFileSync(path.join(OUT, '_errors.txt'), errors.join('\n'));
console.log(`icons: ${count}, errors: ${errors.length}`);
