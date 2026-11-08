// Ports Cobblemon's compiled-in poser definitions to JSON posers (asset step A5, project_v2.md Appendix C.3).
//
// Cobblemon (MPL-2.0) defines the poses of ~250 species in Kotlin classes (e.g. SquirtleModel.kt) instead of JSON files.
// This tool reads those classes and writes the equivalent JSON, in the same format as the 759 posers that already exist
// in resources/assets/cobblemon/bedrock/pokemon/posers.  It never guesses: anything it does not understand is left out
// of the JSON and listed in build/reports/posers-port.md, so the result can be fixed by hand.
//
// Usage: node tools/port-posers.mjs [--resources resources] [--src vendor/cobblemon-src] [--out tools/ported-posers]
//                                   [--report build/reports] [--tag 1.8.4] [--no-fetch] [--validate]
//   --validate   also converts the classes whose species already has a JSON poser and compares the two
//                (a check of this converter, nothing is written)
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { fetchModelSources, MODELS_DIR, POSE_TYPE_FILE } from './lib/cobblemon-source.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..');
const args = process.argv.slice(2), opt = {};
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--no-fetch' || args[i] === '--validate') opt[args[i].slice(2)] = true;
  else if (args[i].startsWith('--')) opt[args[i].slice(2)] = args[++i];
}
const RES = path.resolve(opt.resources ?? path.join(repo, 'resources'));
const SRC = path.resolve(opt.src ?? path.join(repo, 'vendor/cobblemon-src'));
const OUT = path.resolve(opt.out ?? path.join(here, 'ported-posers'));
const REPORT = path.resolve(opt.report ?? path.join(repo, 'build/reports'));
const TAG = opt.tag ?? '1.8.4';
const BEDROCK = path.join(RES, 'assets/cobblemon/bedrock/pokemon');

// ======================= small Kotlin helpers =======================
const stripComments = (s) => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:"])\/\/[^\n]*/g, '$1');
const CLOSE = { '(': ')', '[': ']', '{': '}' };
/** index of the bracket that closes the one at s[i] (strings are skipped) */
function matching(s, i) {
  const stack = [];
  for (let k = i; k < s.length; k++) {
    const c = s[k];
    if (c === '"') { k++; while (k < s.length && s[k] !== '"') { if (s[k] === '\\') k++; k++; } continue; }
    if (CLOSE[c]) stack.push(CLOSE[c]);
    else if (c === ')' || c === ']' || c === '}') { if (stack.pop() !== c) return -1; if (!stack.length) return k; }
  }
  return -1;
}
/** split at top-level commas */
function splitTop(s, sep = ',') {
  const out = []; let depth = 0, cur = '';
  for (let k = 0; k < s.length; k++) {
    const c = s[k];
    if (c === '"') { let j = k + 1; while (j < s.length && s[j] !== '"') { if (s[j] === '\\') j++; j++; } cur += s.slice(k, j + 1); k = j; continue; }
    if (CLOSE[c]) depth++; else if (c === ')' || c === ']' || c === '}') depth--;
    if (c === sep && depth === 0) { out.push(cur); cur = ''; } else cur += c;
  }
  if (cur.trim()) out.push(cur);
  return out.map((x) => x.trim()).filter(Boolean);
}
const strLit = (e) => { const m = /^"((?:[^"\\]|\\.)*)"(?:\.asExpressionLike\(\))?$/.exec(e.trim()); return m && !m[1].includes('$') ? m[1] : null; };
const num = (e, vars) => {
  e = e.trim().replace(/\.toFloat\(\)$/, '');
  if (/^-?(\d+(\.\d*)?|\.\d+)[fF]?$/.test(e)) return parseFloat(e);
  if (/^-?(\d+(\.\d*)?|\.\d+)[fF]?\.toDouble\(\)$/.test(e)) return parseFloat(e);
  const neg = e.startsWith('-'), id = neg ? e.slice(1) : e;
  if (vars && typeof vars[id] === 'number') return neg ? -vars[id] : vars[id];
  return null;
};
const round = (n) => Math.round(n * 1e5) / 1e5;
const call = (e) => { const m = /^([A-Za-z_][\w.]*)\s*\(([\s\S]*)\)$/.exec(e.trim()); return m ? { name: m[1], args: splitTop(m[2]) } : null; };

// ======================= PoseType.kt =======================
function readPoseSets(file) {
  const src = stripComments(fs.readFileSync(file, 'utf8'));
  const names = [...src.matchAll(/^\s*([A-Z_]+)\s*[,;(]/gm)].map((m) => m[1]);
  const sets = {};
  for (const m of src.matchAll(/val\s+(\w+_POSES)\s*=\s*([^\n]+)/g)) sets[m[1]] = m[2].trim();
  return { names: new Set(names), sets };
}
function evalPoseSet(expr, pose, warn, seen = new Set()) {
  // tokens: setOf(A, B) / EnumSet.of(A, B) / EnumSet.allOf(PoseType::class.java) / PoseType.X / X / NAMED_POSES / + / -
  const out = new Set(); let sign = '+';
  const parts = expr.replace(/\s+/g, ' ').match(/(?:setOf|EnumSet\.of|EnumSet\.allOf|EnumSet\.noneOf)\([^)]*\)|emptySet\(\)|[A-Za-z_.]+|[+-]/g) ?? [];
  for (const p of parts) {
    if (p === '+' || p === '-') { sign = p; continue; }
    let items = [];
    if (/^(setOf|EnumSet\.of)\(/.test(p)) items = p.slice(p.indexOf('(') + 1, -1).split(',').map((x) => x.trim().replace(/^PoseType\./, '')).filter(Boolean);
    else if (p.startsWith('EnumSet.allOf(')) items = [...pose.names];
    else if (p.startsWith('EnumSet.noneOf(') || p === 'emptySet()') items = [];
    else {
      const id = p.replace(/^PoseType\./, '');
      if (pose.sets[id] && !seen.has(id)) items = [...evalPoseSet(pose.sets[id], pose, warn, new Set([...seen, id]))];
      else if (pose.names.has(id)) items = [id];
      else { warn(`unknown pose type "${p}"`); continue; }
    }
    for (const it of items) { if (sign === '+') out.add(it); else out.delete(it); }
    sign = '+';
  }
  return out;
}

// ======================= one model class -> JSON poser =======================
// Pose conditions. JSON posers (PoseAdapter.kt in Cobblemon) understand these boolean keys:
//   isBattle, isTouchingWater, isInWaterOrRain, isUnderWater, isDusk, isStandingOnSand, isStandingOnRedSand, isStandingOnSandOrRedSand
const CONDITION_TERMS = [
  [/^it\.isBattling$/, 'isBattle'], [/^it\.isInWater$/, 'isTouchingWater'], [/^it\.isUnderWater$/, 'isUnderWater'],
  [/^it\.isInWaterOrRain$/, 'isInWaterOrRain'], [/^it\.getEntity\(\)\?\.isDusk\(\)$/, 'isDusk'],
];
function parseCondition(body, warn) {
  const flags = {};
  for (let term of body.split('&&').map((t) => t.trim())) {
    let value = true, m = /^(.*?)\s*(==|!=)\s*true$/.exec(term);
    if (m) { term = m[1].trim(); if (m[2] === '!=') value = false; }
    if (term.startsWith('!')) { value = !value; term = term.slice(1).trim(); }
    const sand = /^it\.getEntity\(\)\?\.isStandingOn\(setOf\(([^)]*)\)\)$/.exec(term);
    if (sand) {
      const ids = new Set([...sand[1].matchAll(/"([^"]+)"/g)].map((x) => x[1]));
      const key = ids.size === 2 && ids.has('minecraft:sand') && ids.has('minecraft:red_sand') ? 'isStandingOnSandOrRedSand' : ids.size === 1 && ids.has('minecraft:sand') ? 'isStandingOnSand' : ids.size === 1 && ids.has('minecraft:red_sand') ? 'isStandingOnRedSand' : null;
      if (key) flags[key] = value; else warn(`condition term not supported: ${term}`);
      continue;
    }
    const hit = CONDITION_TERMS.find(([re]) => re.test(term));
    if (hit) flags[hit[1]] = value; else warn(`condition term not supported: ${term}`);
  }
  return flags;
}

const fmt = (n) => String(round(n));
const q = (s) => `'${s}'`;
/** split call arguments into positional and named ("name = value") ones */
function argsOf(list) {
  const positional = [], named = {};
  for (const a of list) { const m = /^(\w+)\s*=\s*([\s\S]+)$/.exec(a); if (m) named[m[1]] = m[2].trim(); else positional.push(a); }
  return { positional, named };
}
const bool = (e) => (e === 'true' ? true : e === 'false' ? false : null);
const DEFAULT_TIME = /^\{\s*state\s*,\s*_\s*,\s*_\s*->\s*state\.animationSeconds\s*\}$/;
const AXIS_NAME = { X_AXIS: 'x', Y_AXIS: 'y', Z_AXIS: 'z' };

function animExpr(e, ctx, warn) {
  e = e.trim();
  if (typeof ctx.vars[e] === 'string') return ctx.vars[e];
  const s = strLit(e); if (s) return s;
  const wc = /^([\s\S]+?)\.withCondition\(([\s\S]+)\)$/.exec(e); // anim.withCondition(expr) -> {"animation": ..., "condition": ...}
  if (wc) {
    const inner = animExpr(wc[1], ctx, warn), key = wc[2].trim(), cond = typeof ctx.vars[key] === 'string' ? ctx.vars[key] : strLit(key);
    if (inner && cond) return { animation: inner, condition: cond };
    warn(`animation condition not understood: ${key.slice(0, 50)}`); return null;
  }
  const c = call(e); if (!c) { warn(`animation not understood: ${e.slice(0, 70)}`); return null; }
  const n = c.name.replace(/^this\./, '');
  const { positional, named } = argsOf(c.args);
  // the walk/swing constructors also take (period, amplitude) positionally after `this`
  const numArg = (k, i, d) => num(named[k] ?? positional[i] ?? String(d), ctx.vars);

  if (n === 'bedrock' || n === 'bedrockStateful' || n === 'bedrockPrimary') {
    if (positional.length >= 2 && positional.slice(0, 2).every((a) => strLit(a))) {
      if (positional.length > 2 || Object.keys(named).length) warn(`extra arguments to ${n}(...) dropped`);
      const fn = { bedrock: 'q.bedrock', bedrockStateful: 'q.bedrock_stateful', bedrockPrimary: 'q.bedrock_primary' }[n];
      return `${fn}(${q(strLit(positional[0]))}, ${q(strLit(positional[1]))})`;
    }
  }
  if (n === 'singleBoneLook') {
    const head = ctx.parts.head;
    if (!head) { warn('singleBoneLook() but the class has no head part'); return null; }
    const flag = (k) => (named[k] === undefined ? false : bool(named[k]));
    const val = (k, d) => { if (named[k] === undefined) return d; const x = num(named[k], ctx.vars); return x === null ? NaN : x; };
    const dx = flag('disableX'), dy = flag('disableY'), ix = flag('invertX'), iy = flag('invertY');
    // same defaults as SingleBoneLookAnimation's secondary constructor: disable -> 0, invert -> -1
    const pm = val('pitchMultiplier', dx ? 0 : ix ? -1 : 1), ym = val('yawMultiplier', dy ? 0 : iy ? -1 : 1);
    const v = [pm, ym, val('maxPitch', 70), val('minPitch', -45), val('maxYaw', 45), val('minYaw', -45)];
    if (positional.length || dx === null || dy === null || ix === null || iy === null || v.some(Number.isNaN)) { warn(`singleBoneLook arguments not understood: ${c.args.join(', ').slice(0, 60)}`); return null; }
    return v.every((x, i) => x === [1, 1, 70, -45, 45, -45][i]) ? `q.look(${q(head)})` : `q.look(${q(head)}, ${v.map(fmt).join(', ')})`;
  }
  const part = (k) => ctx.parts[k];
  const needParts = (keys) => { const miss = keys.filter((k) => !part(k)); if (miss.length) { warn(`${n}: class has no ${miss.join('/')} part`); return false; } return true; };
  const thisFirst = positional.length >= 1 && positional[0] === 'this';
  if (thisFirst && positional.length <= 3 && n === 'BipedWalkAnimation' && needParts(['leftLeg', 'rightLeg'])) {
    const p = numArg('periodMultiplier', 1, 0.6662), a = numArg('amplitudeMultiplier', 2, 1.4);
    if (p !== null && a !== null) return `q.biped_walk(${fmt(p)}, ${fmt(a)}, ${q(part('leftLeg'))}, ${q(part('rightLeg'))})`;
  }
  if (thisFirst && positional.length <= 3 && n === 'QuadrupedWalkAnimation' && needParts(['foreLeftLeg', 'foreRightLeg', 'hindLeftLeg', 'hindRightLeg'])) {
    const p = numArg('periodMultiplier', 1, 0.6662), a = numArg('amplitudeMultiplier', 2, 1.4);
    if (p !== null && a !== null) return `q.quadruped_walk(${fmt(p)}, ${fmt(a)}, ${['foreLeftLeg', 'foreRightLeg', 'hindLeftLeg', 'hindRightLeg'].map((k) => q(part(k))).join(', ')})`;
  }
  if (thisFirst && positional.length <= 3 && n === 'BimanualSwingAnimation' && needParts(['leftArm', 'rightArm'])) {
    const p = numArg('swingPeriodMultiplier', 1, 0.6662), a = numArg('amplitudeMultiplier', 2, 1);
    if (p !== null && a !== null) return `q.bimanual_swing(${fmt(p)}, ${fmt(a)}, ${q(part('leftArm'))}, ${q(part('rightArm'))})`;
  }
  // BiWingedFrame.wingFlap(...) called on the model itself is the same as WingFlapIdleAnimation(this, ...)
  if (((thisFirst && positional.length === 1 && n === 'WingFlapIdleAnimation') || (n === 'wingFlap' && !positional.length)) && needParts(['leftWing', 'rightWing'])) {
    const sine = named.flapFunction && call(named.flapFunction);
    const sa = sine && sine.name === 'sineFunction' ? argsOf(sine.args).named : null;
    const rad = (x) => numRad(x, ctx.vars);
    const axis = AXIS_NAME[String(named.axis ?? '').replace(/^ModelPartTransformation\./, '')];
    if (sa && sa.verticalShift !== undefined && sa.period !== undefined && sa.amplitude !== undefined && axis && DEFAULT_TIME.test((named.timeVariable ?? '').replace(/\s+/g, ' ').trim())) {
      const [vs, pe, am] = [rad(sa.verticalShift), num(sa.period, ctx.vars), num(sa.amplitude, ctx.vars)];
      if (vs !== null && pe !== null && am !== null) return `q.sine_wing_flap(${fmt(am)}, ${fmt(pe)}, ${fmt(vs)}, ${q(axis)}, ${q(part('leftWing'))}, ${q(part('rightWing'))})`;
    }
  }
  warn(`animation not ported: ${e.replace(/\s+/g, ' ').slice(0, 90)}`); return null;
}

function quirkExpr(e, ctx, warn) {
  e = e.trim();
  if (typeof ctx.vars[e] === 'object' && ctx.vars[e]?.quirk) return ctx.vars[e].quirk;
  const m = /^quirk\s*(?:\(([\s\S]*?)\))?\s*\{([\s\S]*)\}$/.exec(e);
  if (!m) { warn(`quirk not understood: ${e.replace(/\s+/g, ' ').slice(0, 70)}`); return null; }
  let min = 8, max = 30, loops = 1;
  if (m[1] && m[1].trim()) {
    const { positional, named } = argsOf(splitTop(m[1]));
    const range = (positional[0] ?? named.secondsBetweenOccurrences ?? '').match(/^(-?[\d.]+)[fF]?\s+to\s+(-?[\d.]+)[fF]?$/);
    const loop = (named.loopTimes ?? '').match(/^(\d+)\.\.(\d+)$/);
    if (range) { min = parseFloat(range[1]); max = parseFloat(range[2]); }
    else if (positional.length || named.secondsBetweenOccurrences) { warn(`quirk timing not understood: ${m[1].trim().slice(0, 60)}`); return null; }
    if (loop) loops = parseInt(loop[2], 10);
    else if (named.loopTimes) { warn(`quirk loopTimes not understood: ${named.loopTimes}`); return null; }
    const known = new Set(['secondsBetweenOccurrences', 'loopTimes']);
    for (const k of Object.keys(named)) if (!known.has(k)) { warn(`quirk argument not ported: ${k}`); return null; }
  }
  let inner = call(m[2].trim()), fn = 'q.bedrock_quirk';
  if (inner && inner.name === 'PrimaryAnimation' && inner.args.length === 1) { fn = 'q.bedrock_primary_quirk'; inner = call(inner.args[0]); } // a quirk that is a primary animation
  if (inner && inner.name === 'bedrockStateful' && inner.args.length === 2 && inner.args.every((a) => strLit(a))) {
    const base = `${q(strLit(inner.args[0]))}, ${q(strLit(inner.args[1]))}`;
    return min === 8 && max === 30 && loops === 1 ? `${fn}(${base})` : `${fn}(${base}, ${fmt(min)}, ${fmt(max)}, ${loops})`;
  }
  warn(`quirk body not understood: ${m[2].trim().replace(/\s+/g, ' ').slice(0, 70)}`); return null;
}

const AXES = { X_AXIS: 0, Y_AXIS: 1, Z_AXIS: 2 };
const numRad = (e, vars) => { // "-10F.toRadians()" -> radians; "90F" is NOT accepted (unit unknown)
  const m = /^(.+?)\.toRadians\(\)$/.exec(e.trim()); if (!m) return null; const d = num(m[1], vars); return d === null ? null : d * Math.PI / 180;
};
function transformExpr(e, ctx, warn) {
  e = e.trim();
  const at = e.indexOf('.createTransformation()');
  if (at < 0) { warn(`transformedParts entry not understood: ${e.replace(/\s+/g, ' ').slice(0, 70)}`); return null; }
  const partExpr = e.slice(0, at).trim(); let part = null;
  if (partExpr === 'rootPart') part = ctx.rootBone;
  else { const g = /^getPart\("([^"]+)"\)$/.exec(partExpr); part = g ? g[1] : ctx.parts[partExpr] ?? null; }
  if (!part) { warn(`transformedParts part not understood: ${partExpr}`); return null; }
  const t = { part };
  let rest = e.slice(at + '.createTransformation()'.length);
  while (rest.trim()) {
    const m = /^\s*\.\s*(\w+)\s*\(/.exec(rest); if (!m) { warn(`transformation not understood: ${rest.trim().slice(0, 60)}`); break; }
    const open = m[0].length - 1, close = matching(rest, open); if (close < 0) { warn('unbalanced transformation'); break; }
    const method = m[1], args = splitTop(rest.slice(open + 1, close)); rest = rest.slice(close + 1);
    if (method === 'addPosition' || method === 'addRotation' || method === 'addRotationDegrees') {
      const key = method === 'addPosition' ? 'position' : 'rotation';
      const conv = method === 'addPosition' || method === 'addRotationDegrees' ? (x) => num(x, ctx.vars) : (x) => { const r = numRad(x, ctx.vars); return r === null ? null : r * 180 / Math.PI; };
      t[key] ??= [0, 0, 0];
      if (args.length === 2 && AXES[args[0].replace(/^ModelPartTransformation\./, '')] !== undefined) {
        const v = conv(args[1]); if (v === null) { warn(`${method} value not understood: ${args[1].slice(0, 40)}`); continue; }
        t[key][AXES[args[0].replace(/^ModelPartTransformation\./, '')]] += round(v);
      } else if (args.length === 3) {
        const v = args.map(conv); if (v.some((x) => x === null)) { warn(`${method} values not understood: ${args.join(', ').slice(0, 50)}`); continue; }
        v.forEach((x, i) => { t[key][i] += round(x); });
      } else warn(`${method} arguments not understood: ${args.join(', ').slice(0, 50)}`);
    } else if (method === 'withVisibility') {
      const a = (args[0] ?? '').replace(/^visibility\s*=\s*/, '').trim();
      if (a === 'true') t.isVisible = 'true'; else if (a === 'false') t.isVisible = '0.0';
      else { const s = typeof ctx.vars[a] === 'string' ? ctx.vars[a] : strLit(a); if (s) t.isVisible = s; else warn(`visibility not understood: ${a.slice(0, 40)}`); }
    } else warn(`transformation method not ported: ${method}`);
  }
  for (const k of ['position', 'rotation']) if (t[k]) t[k] = t[k].map(round);
  return t;
}

function convertClass(src, pose) {
  src = stripComments(src);
  const warnings = [];
  const w = (m) => warnings.push(m);
  const ctx = { vars: {}, parts: {}, rootBone: null };
  let m;
  if ((m = /registerChildWithAllChildren\("([^"]+)"\)/.exec(src))) ctx.rootBone = m[1]; else w('no rootPart found');
  // every "val x = getPart("bone")" (class members such as head, leftLeg, or local part variables)
  for (const g of src.matchAll(/(?:val|var)\s+(\w+)\s*=\s*(?:\w+\.)?getPart\("([^"]+)"\)/g)) ctx.parts[g[1]] = g[2];
  const out = {};
  for (const k of ['portraitScale', 'profileScale']) { m = new RegExp(`${k}\\s*=\\s*(-?[\\d.]+)[fF]?`).exec(src); if (m) out[k] = parseFloat(m[1]); else w(`no ${k}`); }
  for (const k of ['portraitTranslation', 'profileTranslation']) {
    m = new RegExp(`${k}\\s*=\\s*Vec3\\(([^)]*)\\)`).exec(src);
    if (m) out[k] = m[1].split(',').map((x) => round(parseFloat(x))); else w(`no ${k}`);
  }
  if (ctx.rootBone) out.rootBone = ctx.rootBone;

  const fn = /override\s+fun\s+registerPoses\s*\(\s*\)\s*\{/.exec(src);
  if (!fn) { w('no registerPoses()'); return { json: out, warnings, poseCount: 0 }; }
  const open = fn.index + fn[0].length - 1, close = matching(src, open);
  const body = src.slice(open + 1, close);

  // class-level numeric constants (val shoulderOffset = 5.5)
  for (const c of src.matchAll(/^\s*val\s+(\w+)\s*=\s*(-?\d+(?:\.\d+)?)[fF]?\s*$/gm)) ctx.vars[c[1]] = parseFloat(c[2]);
  // class-level Molang strings (val isSheared = "q.has_aspect('sheared')".asExpressionLike())
  for (const c of src.matchAll(/^\s*(?:private\s+)?val\s+(\w+)\s*=\s*("(?:[^"\\]|\\.)*")(?:\.asExpressionLike\(\))?\s*$/gm)) { const s = strLit(c[2]); if (s !== null) ctx.vars[c[1]] = s; }

  const poses = {}, animations = {}, properties = {};
  const stmtStart = /(?:(?:val|var)\s+(\w+)\s*=\s*|(\w+)\s*=\s*)?(registerPose|quirk)\s*[({]|animations\s*\[\s*"([^"]+)"\s*\]\s*=|properties\s*\[\s*"([^"]+)"\s*\]\s*=|(?:val|var)\s+(\w+)\s*=|(\w+)\s*\.\s*\w+\s*=/g;
  const restOfLine = (from) => { const end = body.indexOf('\n', from); return [body.slice(from, end < 0 ? undefined : end).trim(), end < 0 ? body.length : end]; };
  while ((m = stmtStart.exec(body))) {
    const at = m.index;
    if (m[4]) { // animations["x"] = "...".asExpressionLike()
      const [rhs, next] = restOfLine(stmtStart.lastIndex); const s = strLit(rhs);
      if (s) animations[m[4]] = s; else w(`animations["${m[4]}"] not understood: ${rhs.slice(0, 60)}`);
      stmtStart.lastIndex = next; continue;
    }
    if (m[5]) { const [rhs, next] = restOfLine(stmtStart.lastIndex); const s = strLit(rhs); if (s !== null) properties[m[5]] = s; else w(`properties["${m[5]}"] not understood`); stmtStart.lastIndex = next; continue; }
    if (m[3] === 'quirk') { // val blink = quirk { ... }   or   val x = quirk(args) { ... }
      const name = m[1] ?? m[2]; let p = stmtStart.lastIndex - 1, head = '';
      if (body[p] === '(') { const e = matching(body, p); head = body.slice(p, e + 1); p = body.indexOf('{', e); }
      const e2 = matching(body, p); const qe = quirkExpr('quirk' + head + ' ' + body.slice(p, e2 + 1), ctx, w);
      if (name && qe) ctx.vars[name] = { quirk: qe };
      stmtStart.lastIndex = e2 + 1; continue;
    }
    if (m[3] === 'registerPose') {
      const p = stmtStart.lastIndex - 1, e = matching(body, p);
      const pw = [], pwarn = (msg) => pw.push(msg);
      const P = {}; let name = null, types = null;
      for (const a of splitTop(body.slice(p + 1, e))) {
        const eq = /^(\w+)\s*=\s*([\s\S]*)$/.exec(a); if (!eq) { pwarn(`positional argument ignored: ${a.slice(0, 40)}`); continue; }
        const [, key, val] = eq;
        if (key === 'poseName') name = strLit(val);
        else if (key === 'poseType' || key === 'poseTypes') types = evalPoseSet(val, pose, pwarn);
        else if (key === 'animations') { const c = call(val); P.animations = (c ? c.args : []).map((x) => animExpr(x, ctx, pwarn)).filter(Boolean); }
        else if (key === 'quirks') { const c = call(val); P.quirks = (c ? c.args : []).map((x) => quirkExpr(x, ctx, pwarn)).filter(Boolean); }
        else if (key === 'namedAnimations') {
          const c = call(val); const o = {};
          for (const pair of c ? c.args : []) { const t = /^"([^"]+)"\s+to\s+(.+)$/s.exec(pair); const ex = t && animExpr(t[2], ctx, pwarn); if (ex) o[t[1]] = ex; else pwarn(`namedAnimations entry not understood: ${pair.slice(0, 50)}`); }
          if (Object.keys(o).length) P.namedAnimations = o;
        } else if (key === 'transformTicks' || key === 'transformToTicks') { const n = num(val, ctx.vars); if (n === null) pwarn(`${key} not understood: ${val}`); else P[key] = n; }
        else if (key === 'condition') { const b = /^\{([\s\S]*)\}$/.exec(val.trim()); if (b) Object.assign(P, parseCondition(b[1].trim(), pwarn)); else pwarn(`condition not understood: ${val.slice(0, 60)}`); }
        else if (key === 'transformedParts') { const c = call(val); P.transformedParts = (c ? c.args : []).map((x) => transformExpr(x, ctx, pwarn)).filter(Boolean); }
        else if (key === 'onTransitionedInto' || key === 'transitions') pwarn(`${key} is not ported`);
        else pwarn(`argument not understood: ${key}`);
      }
      if (!types || !types.size) { pwarn('no pose types'); types = new Set(); }
      P.poseTypes = [...types];
      const poseName = name ?? (types.size === 1 ? [...types][0].toLowerCase() : null);
      if (!poseName) w('pose without a name skipped');
      else { if (poses[poseName]) w(`pose "${poseName}" defined twice`); poses[poseName] = P; for (const x of pw) w(`pose "${poseName}": ${x}`); }
      stmtStart.lastIndex = e + 1; continue;
    }
    if (m[6]) { // val x = <string or number>
      const [rhs, next] = restOfLine(stmtStart.lastIndex);
      const s = strLit(rhs), n = num(rhs, ctx.vars);
      if (s !== null) ctx.vars[m[6]] = s; else if (n !== null) ctx.vars[m[6]] = n;
      stmtStart.lastIndex = next; continue;
    }
    if (m[7]) { const [, next] = restOfLine(stmtStart.lastIndex); w(`statement ignored: ${body.slice(at, next).trim().slice(0, 70)}`); stmtStart.lastIndex = next; }
  }
  if (Object.keys(animations).length) out.animations = animations;
  if (Object.keys(properties).length) out.properties = properties;
  out.poses = poses;
  return { json: out, warnings, poseCount: Object.keys(poses).length };
}

const sortKeys = (o) => Array.isArray(o) ? o.map(sortKeys) : o && typeof o === 'object' ? Object.fromEntries(Object.keys(o).sort().map((k) => [k, sortKeys(o[k])])) : o;

// ======================= main =======================
const norm = (s) => String(s).toLowerCase().replace(/[^a-z0-9]/g, '');
const { modelsDir, poseTypeFile } = opt['no-fetch']
  ? { modelsDir: path.join(SRC, MODELS_DIR), poseTypeFile: path.join(SRC, POSE_TYPE_FILE) }
  : fetchModelSources(TAG, SRC);
const pose = readPoseSets(poseTypeFile);

// Kotlin classes by normalised poser name
const classes = new Map();
(function walk(d) { for (const e of fs.readdirSync(d, { withFileTypes: true })) { const p = path.join(d, e.name); if (e.isDirectory()) walk(p); else if (/^(.+)Model\.kt$/.test(e.name) && !/^(Pokemon|Posable)/.test(e.name)) classes.set(norm(e.name.replace(/Model\.kt$/, '')), p); } })(modelsDir);

// posers that resolvers ask for, and the ones that already exist as JSON
const wanted = new Map(); // poser name -> Set(folder)
const resolverDir = path.join(BEDROCK, 'resolvers');
for (const folder of fs.readdirSync(resolverDir)) {
  for (const f of fs.readdirSync(path.join(resolverDir, folder)).filter((x) => x.endsWith('.json'))) {
    let j; try { j = JSON.parse(fs.readFileSync(path.join(resolverDir, folder, f), 'utf8')); } catch { continue; }
    for (const v of j.variations ?? []) if (v.poser) { const n = v.poser.replace(/^[^:]+:/, ''); (wanted.get(n) ?? wanted.set(n, new Set()).get(n)).add(folder); }
  }
}
const existing = new Map(); // poser name -> path
(function walk(d) { for (const e of fs.readdirSync(d, { withFileTypes: true })) { const p = path.join(d, e.name); if (e.isDirectory()) walk(p); else if (e.name.endsWith('.json')) existing.set(e.name.slice(0, -5), p); } })(path.join(BEDROCK, 'posers'));
const missing = [...wanted.keys()].filter((n) => !existing.has(n)).sort();

const animIndex = new Set(); // "<file stem>|<animation short name>"
(function walk(d) { for (const e of fs.readdirSync(d, { withFileTypes: true })) { const p = path.join(d, e.name); if (e.isDirectory()) walk(p); else if (e.name.endsWith('.animation.json')) { const stem = e.name.slice(0, -15); try { for (const k of Object.keys(JSON.parse(fs.readFileSync(p, 'utf8')).animations ?? {})) animIndex.add(stem + '|' + k.split('.').slice(2).join('.')); } catch { } } } })(path.join(BEDROCK, 'animations'));

function missingAnimations(json) {
  const bad = new Set();
  const check = (x) => { for (const m of String(typeof x === 'string' ? x : x?.animation ?? '').matchAll(/q\.bedrock(?:_stateful|_primary|_quirk)?\('([^']+)',\s*'([^']+)'/g)) if (!animIndex.has(m[1] + '|' + m[2])) bad.add(`${m[1]}/${m[2]}`); };
  Object.values(json.animations ?? {}).forEach(check);
  for (const p of Object.values(json.poses ?? {})) { (p.animations ?? []).forEach(check); (p.quirks ?? []).forEach(check); Object.values(p.namedAnimations ?? {}).forEach(check); }
  return [...bad];
}

// Every bone a poser names (root, look/walk/wing animations, transformedParts) must exist in the species' model files.
const boneCache = new Map();
function boneNames(folder) {
  if (!boneCache.has(folder)) {
    const set = new Set(), dir = path.join(BEDROCK, 'models', folder);
    if (fs.existsSync(dir)) for (const e of fs.readdirSync(dir)) if (e.endsWith('.geo.json')) {
      try { for (const g of JSON.parse(fs.readFileSync(path.join(dir, e), 'utf8'))['minecraft:geometry'] ?? []) for (const b of g.bones ?? []) set.add(b.name); } catch { }
    }
    boneCache.set(folder, set);
  }
  return boneCache.get(folder);
}
function missingBones(json, folder) {
  const have = boneNames(folder); if (!have.size) return [];
  const used = new Set(); if (json.rootBone) used.add(json.rootBone);
  const scan = (x) => {
    const str = typeof x === 'string' ? x : x?.animation ?? ''; let m;
    if ((m = /^q\.look\('([^']+)'/.exec(str))) used.add(m[1]);
    if ((m = /^q\.(?:biped_walk|bimanual_swing|quadruped_walk)\((.*)\)$/.exec(str))) for (const b of m[1].matchAll(/'([^']+)'/g)) used.add(b[1]);
    if ((m = /^q\.sine_wing_flap\((.*)\)$/.exec(str))) [...m[1].matchAll(/'([^']+)'/g)].slice(1).forEach((b) => used.add(b[1])); // the first quoted value is the axis
  };
  for (const p of Object.values(json.poses ?? {})) { (p.animations ?? []).forEach(scan); for (const t of p.transformedParts ?? []) used.add(t.part); }
  return [...used].filter((b) => !have.has(b));
}

const results = []; // for the report
fs.rmSync(OUT, { recursive: true, force: true });
for (const name of missing) {
  const file = classes.get(norm(name));
  const folder = [...wanted.get(name)].sort()[0];
  if (!file) { results.push({ name, folder, status: 'no class', warnings: ['no Kotlin model class with this name'] }); continue; }
  let r; try { r = convertClass(fs.readFileSync(file, 'utf8'), pose); } catch (e) { results.push({ name, folder, status: 'failed', warnings: [e.message] }); continue; }
  const missingAnims = missingAnimations(r.json);
  const warnings = [...r.warnings, ...missingAnims.map((a) => `animation not found in resources: ${a}`), ...missingBones(r.json, folder).map((b) => `bone not found in the model: ${b}`)];
  const usable = r.poseCount > 0 && r.json.rootBone;
  if (usable) {
    const dest = path.join(OUT, folder, name + '.json');
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, JSON.stringify(sortKeys(r.json), null, 2) + '\n');
  }
  results.push({ name, folder, status: !usable ? 'failed' : warnings.length ? 'partial' : 'full', warnings, poses: r.poseCount });
}

// ---- optional check of the converter itself (--validate) ----
// 1. Calibration: the same bone check, run on the 759 posers that Cobblemon ships as JSON. If the check is sound they all pass.
// 2. The 22 species that have a JSON poser AND an old Kotlin class: the classes are stale (Cobblemon moved those species to JSON and
//    changed them), so they are not compared; they only have to parse without errors.
let validation = null;
if (opt.validate) {
  const calibration = { checked: 0, ok: 0, failures: [] };
  for (const [name, file] of existing) {
    const folder = path.basename(path.dirname(file));
    if (!fs.existsSync(path.join(BEDROCK, 'models', folder))) continue;
    let json; try { json = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { continue; }
    calibration.checked++;
    const bad = missingBones(json, folder);
    if (!bad.length) calibration.ok++; else if (calibration.failures.length < 12) calibration.failures.push(`${name}: ${bad.slice(0, 3).join(', ')}`);
  }
  let legacy = 0, legacyOk = 0;
  for (const [key, file] of classes) {
    if (![...existing.keys()].some((n) => norm(n) === key)) continue;
    legacy++;
    try { if (convertClass(fs.readFileSync(file, 'utf8'), pose).poseCount > 0) legacyOk++; } catch { }
  }
  validation = { calibration, legacy, legacyOk };
}

// ======================= report =======================
const by = (s) => results.filter((r) => r.status === s);
const lines = [];
lines.push('# Poser port report\n');
lines.push(`Generated by \`tools/port-posers.mjs\` from Cobblemon source tag \`${TAG}\`. Do not edit by hand.\n`);
lines.push(`- Posers named by resolvers: ${wanted.size}; with a JSON file already: ${wanted.size - missing.length}; **without: ${missing.length}**`);
lines.push(`- Species folders affected: ${new Set(missing.flatMap((n) => [...wanted.get(n)])).size}`);
lines.push(`- Ported **completely** (nothing left out): **${by('full').length}**`);
lines.push(`- Ported **partly** (JSON written, some detail left out): **${by('partial').length}**`);
lines.push(`- **Not ported** (no class found or unusable): **${by('no class').length + by('failed').length}**\n`);
if (by('no class').length + by('failed').length) {
  lines.push('## Not ported\n', '| Poser | Folder | Reason |', '|---|---|---|', ...[...by('no class'), ...by('failed')].map((r) => `| ${r.name} | ${r.folder} | ${r.warnings[0]} |`), '');
}
if (by('partial').length) {
  lines.push('## Ported partly\n');
  const kinds = new Map();
  for (const r of by('partial')) for (const x of r.warnings) { const k = x.replace(/^pose "[^"]*": /, '').replace(/:.*$/, '').replace(/\d+/g, 'N'); kinds.set(k, (kinds.get(k) ?? 0) + 1); }
  lines.push('Most common things left out:\n', '| What | Times |', '|---|---:|', ...[...kinds].sort((a, b) => b[1] - a[1]).slice(0, 20).map(([k, v]) => `| ${k} | ${v} |`), '');
  lines.push('<details><summary>Per species</summary>\n', ...by('partial').map((r) => `- **${r.name}** (${r.folder}): ${r.warnings.slice(0, 6).join('; ')}${r.warnings.length > 6 ? ` (+${r.warnings.length - 6} more)` : ''}`), '\n</details>\n');
}
if (validation) {
  const c = validation.calibration;
  lines.push('## Check of the converter\n',
    `- **Bone check on Cobblemon's own JSON posers** (calibration): ${c.ok} of ${c.checked} pass. The same check is applied to every ported poser (a bone named in the poser must exist in the species' model files).`,
    ...(c.failures.length ? ['  - failures: ' + c.failures.join('; ')] : []),
    `- ${validation.legacy} species have both a JSON poser and an old Kotlin class. The classes are out of date (different poses and portrait values) so they are not compared; ${validation.legacyOk} of them parse without error.\n`);
}
fs.mkdirSync(REPORT, { recursive: true });
fs.writeFileSync(path.join(REPORT, 'posers-port.md'), lines.join('\n'));
fs.writeFileSync(path.join(REPORT, 'posers-port.json'), JSON.stringify(results, null, 1));
if (by('full').length + by('partial').length) {
  fs.writeFileSync(path.join(OUT, 'NOTICE.md'), `# Ported posers\n\nThese JSON files were generated by \`tools/port-posers.mjs\` from the Kotlin model classes of Cobblemon (tag ${TAG}, https://gitlab.com/cable-mc/cobblemon), which are licensed under the Mozilla Public License 2.0 (Copyright (C) 2023 Cobblemon Contributors). They are derived works and stay under MPL-2.0; see \`../../LICENSE\` of the Cobblemon jar (copied to \`resources/LICENSE\`). They may be edited by hand; re-running the tool overwrites them.\n`);
}
console.log(`posers without JSON: ${missing.length} | full: ${by('full').length} | partial: ${by('partial').length} | not ported: ${by('no class').length + by('failed').length}`);
if (validation) console.log(`bone check on Cobblemon's own posers: ${validation.calibration.ok} of ${validation.calibration.checked} pass; legacy Kotlin classes parsed: ${validation.legacyOk} of ${validation.legacy}`);
console.log('report:', path.join(REPORT, 'posers-port.md'));
