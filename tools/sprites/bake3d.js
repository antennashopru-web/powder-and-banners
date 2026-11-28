/* Выгрузка 3D-моделей для настоящего 3D в игре (OpenGL ES на телефоне).
   Каждая модель «запекается» в набор кадров с общей топологией:
   индексы треугольников, цвета вершин для каждой команды и позиции+нормали для каждого кадра.
   Анимации персонажей KayKit (скелет) запекаются в позы, игра плавно смешивает соседние кадры. */
'use strict';
const BAKE_Q = 32; // позиция хранится как int16 = round(px * 32)

const texCache = new Map();
function texData(tex) {
  if (!tex || !tex.image) return null;
  if (texCache.has(tex)) return texCache.get(tex);
  const im = tex.image, c = document.createElement('canvas');
  c.width = im.width; c.height = im.height;
  const g = c.getContext('2d'); g.drawImage(im, 0, 0);
  const d = { w: c.width, h: c.height, p: g.getImageData(0, 0, c.width, c.height).data };
  texCache.set(tex, d); return d;
}
function sample(tex, u, v) {
  const d = texData(tex); if (!d) return [1, 1, 1];
  if (tex.repeat) { u *= tex.repeat.x; v *= tex.repeat.y; }
  u -= Math.floor(u); v -= Math.floor(v);
  if (tex.flipY) v = 1 - v;
  const x = Math.min(d.w - 1, Math.floor(u * d.w)), y = Math.min(d.h - 1, Math.floor(v * d.h));
  const i = (y * d.w + x) * 4;
  return [d.p[i] / 255, d.p[i + 1] / 255, d.p[i + 2] / 255];
}
function visibleChain(o) { for (let p = o; p; p = p.parent) if (!p.visible) return false; return true; }

// список мешей и их частей (материалов) в постоянном порядке
function parts(root) {
  const out = [];
  root.updateMatrixWorld(true);
  root.traverse((o) => {
    if (!o.isMesh || !visibleChain(o)) return;
    const geo = o.geometry, mats = Array.isArray(o.material) ? o.material : [o.material];
    const idx = geo.index ? geo.index.array : null, n = idx ? idx.length : geo.attributes.position.count;
    const groups = geo.groups.length ? geo.groups : [{ start: 0, count: n, materialIndex: 0 }];
    for (const gr of groups) out.push({ o, mat: mats[gr.materialIndex] || mats[0], start: gr.start, count: Math.min(gr.count, n - gr.start) });
  });
  return out;
}

// один кадр: позиции и нормали всех вершин (без индексации, по треугольникам)
function bakeFrame(root, withColor) {
  const P = [], N = [], C = [], UV = [], TX = [];
  const v = new THREE.Vector3(), a = new THREE.Vector3(), b = new THREE.Vector3(), c = new THREE.Vector3();
  const nrm = new THREE.Vector3(), nm = new THREE.Matrix3();
  for (const pt of parts(root)) {
    const o = pt.o, geo = o.geometry, pos = geo.attributes.position, uv = geo.attributes.uv, col = geo.attributes.color, nor = geo.attributes.normal;
    const idx = geo.index ? geo.index.array : null;
    nm.getNormalMatrix(o.matrixWorld);
    let base = null;
    if (withColor) {
      const m = pt.mat, cc = m.color ? m.color.clone().convertLinearToSRGB() : new THREE.Color(1, 1, 1);
      const em = m.emissive ? m.emissive.clone().convertLinearToSRGB() : new THREE.Color(0, 0, 0);
      base = { m, r: cc.r, g: cc.g, b: cc.b, er: em.r, eg: em.g, eb: em.b };
    }
    const tri = [];
    const textured = !!(base && base.m.map && uv && o.userData.triAvg && base.m.map.image);
    if (withColor) {
      for (let k = 0; k + 2 < pt.count; k += 3) {
        if (!textured) { for (let q = 0; q < 6; q++) UV.push(-1); TX.push(null); continue; }
        const ii = [0, 1, 2].map((q) => idx ? idx[pt.start + k + q] : pt.start + k + q);
        let U = ii.map((i) => uv.getX(i)), V = ii.map((i) => uv.getY(i));
        if (base.m.map.flipY) V = V.map((v) => 1 - v);
        // треугольник целиком внутрь [0,1]: сдвиг на целое, затем зажим
        const su = Math.floor(Math.min(...U)), sv = Math.floor(Math.min(...V));
        for (let q = 0; q < 3; q++) UV.push(Math.min(1, Math.max(0, U[q] - su)), Math.min(1, Math.max(0, V[q] - sv)));
        TX.push(base.m.map);
      }
    }
    for (let k = 0; k < pt.count; k++) {
      const i = idx ? idx[pt.start + k] : pt.start + k;
      o.getVertexPosition(i, v); v.applyMatrix4(o.matrixWorld);
      tri.push(v.x, v.y, v.z);
      if (base) {
        let r = base.r, g = base.g, bl = base.b;
        if (base.m.map && uv && !textured) { const s = sample(base.m.map, uv.getX(i), uv.getY(i)); r *= s[0]; g *= s[1]; bl *= s[2]; }
        if (col && base.m.vertexColors) { r *= col.getX(i); g *= col.getY(i); bl *= col.getZ(i); }
        C.push(Math.min(1, r + base.er), Math.min(1, g + base.eg), Math.min(1, bl + base.eb));
      }
    }
    // нормали: плоские по треугольнику (модели низкополигональные, так выглядят чище)
    for (let t = 0; t + 8 < tri.length; t += 9) {
      a.set(tri[t], tri[t + 1], tri[t + 2]); b.set(tri[t + 3], tri[t + 4], tri[t + 5]); c.set(tri[t + 6], tri[t + 7], tri[t + 8]);
      nrm.subVectors(c, b).cross(b.clone().sub(a)).normalize();
      if (!isFinite(nrm.x)) nrm.set(0, 1, 0);
      for (let q = 0; q < 3; q++) { P.push(tri[t + q * 3], tri[t + q * 3 + 1], tri[t + q * 3 + 2]); N.push(-nrm.x, -nrm.y, -nrm.z); }
    }
  }
  return { P, N, C, UV, TX };
}

// атлас текстур модели: 1024×1024, сетка g×g; uv переносятся в свою клетку (с полями от просачивания)
const ATLAS = 1024;
function buildAtlas(UV, TX) {
  const texs = [...new Set(TX.filter((t) => t))];
  if (!texs.length) return null;
  const g = Math.ceil(Math.sqrt(texs.length)), cs = ATLAS / g, m = 6;
  const cv = document.createElement('canvas'); cv.width = cv.height = ATLAS;
  const cx = cv.getContext('2d'); cx.fillStyle = '#fff'; cx.fillRect(0, 0, ATLAS, ATLAS);
  texs.forEach((t, k) => {
    const x = (k % g) * cs, y = Math.floor(k / g) * cs;
    cx.drawImage(t.image, x, y, cs, cs);
  });
  const out = new Float32Array(UV.length);
  for (let tri = 0; tri < TX.length; tri++) {
    const t = TX[tri];
    for (let q = 0; q < 3; q++) {
      const j = (tri * 3 + q) * 2;
      if (!t) { out[j] = -1; out[j + 1] = -1; continue; }
      const k = texs.indexOf(t), x = (k % g) * cs, y = Math.floor(k / g) * cs;
      out[j] = (x + m + UV[j] * (cs - 2 * m)) / ATLAS; out[j + 1] = (y + m + UV[j + 1] * (cs - 2 * m)) / ATLAS;
    }
  }
  return { uv: out, url: cv.toDataURL('image/jpeg', 0.86) };
}
function enc16uv(arr) { const o = new Int16Array(arr.length); for (let i = 0; i < arr.length; i++) o[i] = arr[i] < 0 ? -32767 : Math.round(arr[i] * 32767); return o; }

function enc16(arr) { const o = new Int16Array(arr.length); for (let i = 0; i < arr.length; i++) o[i] = Math.max(-32767, Math.min(32767, Math.round(arr[i] * BAKE_Q))); return o; }
function enc8n(arr) { const o = new Int8Array(arr.length); for (let i = 0; i < arr.length; i++) o[i] = Math.round(arr[i] * 127); return o; }
function enc8c(arr) { const o = new Uint8Array(arr.length); for (let i = 0; i < arr.length; i++) o[i] = Math.round(Math.max(0, Math.min(1, arr[i])) * 255); return o; }

// модель: makeFor(team, frame) -> Object3D. Цвета берутся из кадра 0 каждой команды.
// Нормали не хранятся: игра считает плоские нормали в шейдере. Вершины с одинаковыми
// позициями во всех кадрах и одинаковыми цветами объединяются (индексированная сетка).
function bakeModel(makeFor, teams, frames) {
  const cols = [], P = [];
  let n = -1, atlas = null, UVq = null;
  for (let t = 0; t < teams; t++) {
    const f0 = bakeFrame(makeFor(t, 0), true);
    cols.push(enc8c(f0.C));
    if (t === 0) { n = f0.P.length / 3; P.push(enc16(f0.P)); atlas = buildAtlas(f0.UV, f0.TX); if (atlas) UVq = enc16uv(atlas.uv); }
    else if (f0.P.length / 3 !== n) throw new Error('team topology differs');
  }
  for (let f = 1; f < frames; f++) {
    const fr = bakeFrame(makeFor(0, f), false);
    if (fr.P.length / 3 !== n) throw new Error('frame ' + f + ' topology differs: ' + fr.P.length / 3 + ' vs ' + n);
    P.push(enc16(fr.P));
  }
  const map = new Map(), remap = new Uint32Array(n); let u = 0; const uniq = [];
  for (let i = 0; i < n; i++) {
    let key = '';
    for (const pf of P) key += pf[i * 3] + ',' + pf[i * 3 + 1] + ',' + pf[i * 3 + 2] + ';';
    for (const c of cols) key += c[i * 3] + ',' + c[i * 3 + 1] + ',' + c[i * 3 + 2] + ';';
    if (UVq) key += UVq[i * 2] + ',' + UVq[i * 2 + 1];
    let k = map.get(key);
    if (k === undefined) { k = u++; map.set(key, k); uniq.push(i); }
    remap[i] = k;
  }
  if (u > 65535) throw new Error('too many verts ' + u);
  const idx = new Uint16Array(n); for (let i = 0; i < n; i++) idx[i] = remap[i];
  const pick = (src, comp, Ctor) => { const o = new Ctor(u * comp); uniq.forEach((i, k) => { for (let c = 0; c < comp; c++) o[k * comp + c] = src[i * comp + c]; }); return o; };
  return { n: u, tris: n / 3, idx, colors: cols.map((c) => pick(c, 3, Uint8Array)), pos: P.map((p) => pick(p, 3, Int16Array)),
    uv: UVq ? pick(UVq, 2, Int16Array) : null, tex: atlas ? atlas.url : null };
}

const BAKE_FRAMES = [{ kind: 'idle' }, ...[0, 1, 2, 3, 4, 5, 6, 7].map((i) => ({ kind: 'walk', i: i / 2, ph: i * Math.PI / 4 })), ...[0, 1, 2].map((i) => ({ kind: 'act', i })), { kind: 'dead' }];
function deadWrap(type, obj) {
  if (type === 'cav' || type === 'dragoon') { const w = new THREE.Group(); obj.rotation.x = Math.PI / 2 * 0.95; obj.position.y = 5; w.add(obj); return w; }
  if (type === 'cannon' || type === 'mortar') { obj.rotation.z = 0.3; obj.position.y = -2.5; }
  return obj;
}

window.bakeAll = async function () {
  await kkLoad();
  const out = {}, t0 = Date.now();
  const U = { peasant: kkPeasant, pike: kkPike, musket: kkMusket, cav: kkCav, cannon: kkCannon, dragon: kkDragon, grenadier: kkGrenadier, dragoon: kkDragoon, mortar: kkMortar, hero: kkHero, siege: kkSiege };
  for (const k in U) {
    out['u_' + k] = bakeModel((team, f) => {
      const fr = BAKE_FRAMES[f];
      if (fr.kind === 'dead') return deadWrap(k, U[k](team, (k === 'cav' || k === 'cannon' || k === 'dragoon' || k === 'mortar') ? { kind: 'idle' } : fr));
      return U[k](team, fr);
    }, 2, BAKE_FRAMES.length);
  }
  for (const k in KK_BLD) out['b_' + k] = bakeModel((team) => KK_BLD[k](team), 2, 1);
  for (let k = 0; k < 8; k++) out['tree' + k] = bakeModel(() => kkTree(k), 1, 1);
  out.rock_gold = bakeModel(() => kkRocks('gold'), 1, 1);
  out.rock_stone = bakeModel(() => kkRocks('stone'), 1, 1);
  out.scaffold = bakeModel(() => fitModel(KK.med.scaffold, 64, 64), 1, 1);
  out.wall_seg = bakeModel((team) => kkWallSeg(team), 2, 1);
  DECO.forEach((f, k) => { out['deco' + k] = bakeModel(() => f(), 1, 1); });
  // упаковка: JSON-оглавление + один двоичный блок
  const index = {}, chunks = [], texOut = {}; let off = 0;
  const push = (ta) => { const pad = (4 - (off % 4)) % 4; if (pad) { chunks.push(new Uint8Array(pad)); off += pad; } const u8 = new Uint8Array(ta.buffer, ta.byteOffset, ta.byteLength); chunks.push(u8); const o = off; off += u8.byteLength; return o; };
  for (const name in out) {
    const m = out[name];
    index[name] = { n: m.n, tris: m.tris, frames: m.pos.length, idx: push(m.idx), colors: m.colors.map(push), pos: m.pos.map(push) };
    if (m.uv) { index[name].uv = push(m.uv); index[name].tex = 'tex_' + name + '.jpg'; texOut[index[name].tex] = m.tex; }
  }
  const all = new Uint8Array(off); let p = 0; for (const c of chunks) { all.set(c, p); p += c.byteLength; }
  let s = ''; for (let i = 0; i < all.length; i += 0x8000) s += String.fromCharCode.apply(null, all.subarray(i, i + 0x8000));
  return { index: { q: BAKE_Q, frames: BAKE_FRAMES.map((f) => f.kind), models: index }, bin: btoa(s), tex: texOut, ms: Date.now() - t0 };
};
