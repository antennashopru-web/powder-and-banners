'use strict';
/* Порох и Знамёна — стратегия в реальном времени. Весь код игры в одном файле. */
(function () {

// ============================================================ константы
const TILE = 32, CH = 16, CPAD = 48;
const T_GRASS = 0, T_FOREST = 1, T_WATER = 2, T_SAND = 3;
const TEAM = [
  { main: '#2e62c4', dark: '#1b3a78', light: '#86aef0', name: 'Синие' },
  { main: '#c0342b', dark: '#78201a', light: '#f08a80', name: 'Красные' },
];
const RES = ['food', 'wood', 'stone', 'gold'];
const RICON = { food: '🌾', wood: '🌲', stone: '⛰️', gold: '💰' };
const RNAME = { food: 'Еда', wood: 'Дерево', stone: 'Камень', gold: 'Золото' };
const CARRY = 10;
const GTIME = { food: 0.8, wood: 0.9, stone: 1.1, gold: 1.2 };
const POP_MAX = 300;

const UNIT = {
  peasant: { name: 'Крестьянин', hp: 45, atk: 3, range: 14, cd: 1.2, spd: 54, sight: 6, cost: { food: 50 }, time: 8, pop: 1, r: 6, desc: 'Добывает ресурсы и строит' },
  pike: { name: 'Пикинёр', hp: 120, atk: 9, range: 22, cd: 1.3, spd: 44, sight: 6, cost: { food: 40, wood: 20 }, time: 9, pop: 1, r: 6, vsCav: 2.6, desc: 'Стойкая пехота, сильна против конницы' },
  musket: { name: 'Мушкетёр', hp: 70, atk: 26, range: 190, cd: 4.2, spd: 42, sight: 8, cost: { food: 50, wood: 10, gold: 20 }, time: 11, pop: 1, r: 6, ranged: true, desc: 'Мощный выстрел, долгая перезарядка' },
  cav: { name: 'Рейтар', hp: 180, atk: 16, range: 22, cd: 1.1, spd: 96, sight: 7, cost: { food: 80, gold: 50 }, time: 13, pop: 2, r: 9, cav: true, desc: 'Быстрая тяжёлая конница' },
  cannon: { name: 'Пушка', hp: 150, atk: 70, range: 340, cd: 7, spd: 26, sight: 9, cost: { wood: 150, stone: 80, gold: 120 }, time: 20, pop: 3, r: 9, ranged: true, splash: 46, vsBld: 3, desc: 'Рушит здания и плотные строи' },
};
const FORM_ORDER = { pike: 0, musket: 1, cav: 2, cannon: 3, peasant: 4 };

const BLD = {
  hall: { name: 'Ратуша', w: 3, h: 3, hp: 2400, cost: { wood: 300, stone: 200 }, time: 60, pop: 15, drop: true, trains: ['peasant'], sight: 9, atk: 8, range: 200, cd: 2, desc: 'Сердце поселения: крестьяне и склад' },
  house: { name: 'Дом', w: 2, h: 2, hp: 500, cost: { wood: 60 }, time: 15, pop: 10, sight: 4, desc: '+10 к населению' },
  farm: { name: 'Ферма', w: 3, h: 3, hp: 400, cost: { wood: 80 }, time: 18, sight: 4, farm: true, desc: 'Даёт еду, до 6 крестьян' },
  store: { name: 'Склад', w: 2, h: 2, hp: 600, cost: { wood: 100 }, time: 18, drop: true, sight: 5, desc: 'Сюда можно сдавать добычу' },
  barracks: { name: 'Казарма', w: 3, h: 3, hp: 1300, cost: { wood: 150, stone: 50 }, time: 35, trains: ['pike', 'musket'], sight: 6, desc: 'Пикинёры и мушкетёры' },
  stable: { name: 'Конюшня', w: 3, h: 3, hp: 1200, cost: { wood: 200, stone: 50, gold: 50 }, time: 40, trains: ['cav'], sight: 6, desc: 'Рейтары' },
  foundry: { name: 'Литейня', w: 3, h: 3, hp: 1400, cost: { wood: 200, stone: 150, gold: 100 }, time: 50, trains: ['cannon'], sight: 6, desc: 'Пушки' },
  tower: { name: 'Башня', w: 2, h: 2, hp: 1000, cost: { wood: 50, stone: 150 }, time: 30, sight: 9, atk: 15, range: 230, cd: 1.8, desc: 'Стреляет по врагам' },
};
const BUILD_LIST = ['house', 'farm', 'store', 'barracks', 'stable', 'foundry', 'tower', 'hall'];

const DIFF = {
  easy: { mult: 0.7, peas: 16, wave: 6, grow: 3, interval: 260, first: 620, pace: 1.5, cannons: 1, bonus: 0 },
  normal: { mult: 1.0, peas: 24, wave: 10, grow: 4, interval: 210, first: 450, pace: 1.0, cannons: 2, bonus: 100 },
  hard: { mult: 1.4, peas: 32, wave: 12, grow: 5, interval: 170, first: 330, pace: 0.75, cannons: 4, bonus: 300 },
};

const DIRS = [[1, 0, 1], [-1, 0, 1], [0, 1, 1], [0, -1, 1], [1, 1, 1.414], [1, -1, 1.414], [-1, 1, 1.414], [-1, -1, 1.414]];

// ============================================================ утилиты
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
const $ = (id) => document.getElementById(id);
function hash2(x, y, s) {
  let h = Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263) ^ Math.imul(s | 0, 982451653);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}
function vnoise(x, y, s) {
  const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
  const u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
  const a = hash2(xi, yi, s), b = hash2(xi + 1, yi, s), c = hash2(xi, yi + 1, s), d = hash2(xi + 1, yi + 1, s);
  return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
}
function fbm(x, y, s) { return vnoise(x, y, s) * 0.6 + vnoise(x * 2, y * 2, s + 7) * 0.3 + vnoise(x * 4, y * 4, s + 13) * 0.1; }
const dirOf = (dx, dy) => Math.round(Math.atan2(dy, dx) / (Math.PI / 4)) & 7;
function costStr(c) { return RES.filter((r) => c[r]).map((r) => RICON[r] + c[r]).join(' '); }

class Heap {
  constructor() { this.a = []; this.c = []; this.lastC = 0; }
  get size() { return this.a.length; }
  push(v, c) {
    const a = this.a, cs = this.c; let i = a.length; a.push(v); cs.push(c);
    while (i > 0) { const p = (i - 1) >> 1; if (cs[p] <= c) break; a[i] = a[p]; cs[i] = cs[p]; i = p; }
    a[i] = v; cs[i] = c;
  }
  pop() {
    const a = this.a, cs = this.c; const top = a[0], tc = cs[0];
    const lv = a.pop(), lc = cs.pop(); const n = a.length;
    if (n) {
      let i = 0;
      for (;;) {
        const l = 2 * i + 1, r = l + 1; let m = i, mc = lc;
        if (l < n && cs[l] < mc) { m = l; mc = cs[l]; }
        if (r < n && cs[r] < mc) { m = r; mc = cs[r]; }
        if (m === i) break;
        a[i] = a[m]; cs[i] = cs[m]; i = m;
      }
      a[i] = lv; cs[i] = lc;
    }
    this.lastC = tc; return top;
  }
}

// ============================================================ холст
const cv = $('cv'), ctx = cv.getContext('2d');
let DPR = 1, VW = 0, VH = 0;
function resize() {
  DPR = Math.min(window.devicePixelRatio || 1, 2);
  VW = window.innerWidth; VH = window.innerHeight;
  cv.width = Math.round(VW * DPR); cv.height = Math.round(VH * DPR);
  cv.style.width = VW + 'px'; cv.style.height = VH + 'px';
  if (G) clampCam();
}
window.addEventListener('resize', resize);

// ============================================================ состояние
let G = null;
const opts = { diff: 'normal', size: 80 };
let soundOn = true;

function idx(tx, ty) { return ty * G.W + tx; }
function inB(tx, ty) { return tx >= 0 && ty >= 0 && tx < G.W && ty < G.H; }
function walk(i) { const t = G.terrain[i]; return (t === T_GRASS || t === T_SAND) && G.occ[i] === 0; }
function walkXY(tx, ty) { return inB(tx, ty) && walk(ty * G.W + tx); }
function walkPx(x, y) { return walkXY(Math.floor(x / TILE), Math.floor(y / TILE)); }

function newGame(o, auto) {
  const W = o.size, H = o.size;
  G = {
    W, H, seed: (Math.random() * 1e9) | 0,
    terrain: new Uint8Array(W * H), wood: new Uint16Array(W * H), occ: new Int32Array(W * H),
    units: [], blds: [], mines: [], proj: [], fx: [], decals: [], markers: [],
    byId: new Map(), nextId: 1, time: 0, fieldVer: 1, fields: new Map(),
    explored: new Uint8Array(W * H), visible: new Uint8Array(W * H),
    res: [{ food: 300, wood: 400, stone: 200, gold: 150 }, { food: 300, wood: 400, stone: 200, gold: 150 }],
    pop: [0, 0], cap: [0, 0],
    stats: [0, 1].map(() => ({ killed: 0, lost: 0, trained: 0, built: 0, gathered: 0 })),
    sel: [], placing: null, amove: false, boxMode: false,
    cam: { x: 0, y: 0, z: 1 }, speed: 1, paused: false, over: false,
    fogT: 0, aiT: 0, endT: 0, uiT: 0, miniT: 0, lastAlert: -99, ping: null,
    chunks: new Map(), miniDirty: true, auto: !!auto,
    uHead: new Int32Array(W * H), uNext: new Int32Array(4096),
    ai: [null, null],
  };
  const P = DIFF[o.diff];
  G.res[1].food += P.bonus; G.res[1].wood += P.bonus; G.res[1].gold += P.bonus >> 1;
  genMap();
  G.ai[1] = { diff: o.diff, wave: 0, nextAttack: P.first, home: null };
  if (auto) G.ai[0] = { diff: 'normal', wave: 0, nextAttack: DIFF.normal.first, home: null };
  computePop();
  updateFog(true);
  const h = G.blds.find((b) => b.team === 0);
  G.cam.z = VH < 500 ? 0.95 : 1.15;
  centerCam(h.x, h.y);
  buildMiniBase();
}

// ============================================================ генерация карты
function genMap() {
  const { W, H, seed } = G;
  const bases = [{ x: 6, y: H - 10 }, { x: W - 10, y: 6 }];
  const centers = bases.map((b) => ({ x: b.x + 1.5, y: b.y + 1.5 }));
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const n = fbm(x / 8, y / 8, seed), m = fbm(x / 13, y / 13, seed + 101);
      let t = T_GRASS;
      if (m > 0.71) t = T_WATER; else if (m > 0.665) t = T_SAND; else if (n > 0.6) t = T_FOREST;
      for (const c of centers) { if (Math.hypot(x - c.x, y - c.y) < 9) t = T_GRASS; }
      const i = y * W + x; G.terrain[i] = t; if (t === T_FOREST) G.wood[i] = 120;
    }
  }
  // гарантированные рощи и шахты у каждой базы
  centers.forEach((c, k) => {
    const sx = Math.sign(W / 2 - c.x), sy = Math.sign(H / 2 - c.y);
    blob(c.x + sx * 10, c.y + sy * 4, 2.6, T_FOREST);
    blob(c.x + sx * 3, c.y + sy * 11, 2.8, T_FOREST);
    blob(c.x - sx * 2, c.y - sy * 6.5, 1.8, T_FOREST);
    addMine('gold', Math.round(c.x + sx * 7) - 1, Math.round(c.y - sy * 1) - 1, 2500);
    addMine('stone', Math.round(c.x - sx * 1) - 1, Math.round(c.y + sy * 7) - 1, 2500);
  });
  // шахты в середине
  const extra = W >= 90 ? 4 : 3;
  for (let k = 0; k < extra; k++) {
    for (let tries = 0; tries < 200; tries++) {
      const x = Math.floor(W * 0.2 + Math.random() * W * 0.6), y = Math.floor(H * 0.2 + Math.random() * H * 0.6);
      if (centers.some((c) => Math.hypot(x - c.x, y - c.y) < 16)) continue;
      // симметрично для честности
      const x2 = W - 2 - x, y2 = H - 2 - y;
      if (areaFree(x, y, 2, 2) && areaFree(x2, y2, 2, 2)) {
        const kind = k % 2 ? 'stone' : 'gold';
        addMine(kind, x, y, 3000); addMine(kind, x2, y2, 3000); break;
      }
    }
  }
  // базы
  bases.forEach((b, team) => {
    placeBuilding('hall', team, b.x, b.y, true);
    const c = centers[team];
    const sx = Math.sign(W / 2 - c.x), sy = Math.sign(H / 2 - c.y);
    for (let i = 0; i < 6; i++) spawnUnit('peasant', team, (c.x + sx * (2.5 + (i % 3))) * TILE, (c.y + sy * 2.6 + Math.floor(i / 3) * sy) * TILE);
    for (let i = 0; i < 3; i++) spawnUnit('pike', team, (c.x - sx * 2.6) * TILE, (c.y + sy * (i - 1)) * TILE);
  });
  // проходимость между базами
  const f = computeField([idx(Math.floor(centers[1].x), Math.floor(centers[1].y) + 2)]);
  const pi = idx(Math.floor(centers[0].x), Math.floor(centers[0].y) - 2);
  if (f[pi] >= 1e8) carve(centers[0], centers[1]);
}
function blob(cx, cy, r, t) {
  for (let y = Math.floor(cy - r - 1); y <= cy + r + 1; y++) for (let x = Math.floor(cx - r - 1); x <= cx + r + 1; x++) {
    if (!inB(x, y)) continue;
    if (Math.hypot(x - cx, y - cy) <= r + hash2(x, y, 9) * 0.8) { const i = idx(x, y); if (G.occ[i] === 0) { G.terrain[i] = t; if (t === T_FOREST) G.wood[i] = 120; } }
  }
}
function areaFree(x, y, w, h) {
  for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) { if (!inB(i, j)) return false; const k = idx(i, j); if (G.occ[k] || G.terrain[k] === T_WATER) return false; }
  return true;
}
function addMine(kind, x, y, amount) {
  x = clamp(x, 1, G.W - 3); y = clamp(y, 1, G.H - 3);
  const m = { id: G.mines.length + 1, kind: 'm', res: kind, tx: x, ty: y, w: 2, h: 2, x: (x + 1) * TILE, y: (y + 1) * TILE, amount, max: amount, dead: false, team: -1 };
  for (let j = y - 1; j < y + 3; j++) for (let i = x - 1; i < x + 3; i++) {
    if (!inB(i, j)) continue; const k = idx(i, j);
    if (i >= x && j >= y && i < x + 2 && j < y + 2) { G.terrain[k] = T_GRASS; G.wood[k] = 0; G.occ[k] = -m.id; }
    else if (G.terrain[k] !== T_GRASS && G.occ[k] === 0) { G.terrain[k] = T_GRASS; G.wood[k] = 0; }
  }
  G.mines.push(m);
}
function carve(a, b) {
  const n = Math.ceil(Math.hypot(b.x - a.x, b.y - a.y));
  for (let s = 0; s <= n; s++) {
    const x = a.x + (b.x - a.x) * s / n, y = a.y + (b.y - a.y) * s / n;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
      const tx = Math.floor(x + dx), ty = Math.floor(y + dy);
      if (inB(tx, ty)) { const i = idx(tx, ty); if (G.occ[i] === 0) { G.terrain[i] = T_GRASS; G.wood[i] = 0; } }
    }
  }
}

// ============================================================ сущности
function spawnUnit(type, team, x, y) {
  const d = UNIT[type];
  const u = {
    id: G.nextId++, kind: 'u', type, team, x, y, hp: d.hp, maxhp: d.hp, r: d.r, face: team ? -1 : 1, dir: team ? 4 : 0, anim: Math.random() * 6,
    cd: 0, goal: null, target: null, cmd: null, task: null, carry: 0, carryType: null, phase: 'go', workT: 0,
    scanT: Math.random() * 0.4, chaseT: 0, moving: false, working: false, aimT: 0, hitT: 0, dead: false, amovePt: null,
  };
  if (!walkPx(x, y)) { const p = nearestWalkable(x, y); u.x = p.x; u.y = p.y; }
  G.units.push(u); G.byId.set(u.id, u);
  return u;
}
function placeBuilding(type, team, tx, ty, built) {
  const d = BLD[type];
  const b = {
    id: G.nextId++, kind: 'b', type, team, tx, ty, w: d.w, h: d.h, x: (tx + d.w / 2) * TILE, y: (ty + d.h / 2) * TILE,
    hp: built ? d.hp : Math.max(1, d.hp * 0.1), maxhp: d.hp, built: !!built, progress: built ? d.time : 0,
    queue: [], qt: 0, rally: null, cd: 0, target: null, scanT: 0, dead: false, r: d.w * TILE / 2,
  };
  for (let j = ty; j < ty + d.h; j++) for (let i = tx; i < tx + d.w; i++) { const k = idx(i, j); G.occ[k] = b.id; G.terrain[k] = T_GRASS; G.wood[k] = 0; }
  G.blds.push(b); G.byId.set(b.id, b); G.fieldVer++; G.miniDirty = true;
  markChunksRect(tx, ty, d.w, d.h);
  for (const u of G.units) {
    const ux = Math.floor(u.x / TILE), uy = Math.floor(u.y / TILE);
    if (ux >= tx && uy >= ty && ux < tx + d.w && uy < ty + d.h) { const p = exitPoint(b); u.x = p.x; u.y = p.y; u.goal = null; }
  }
  return b;
}
function nearestWalkable(x, y) {
  const cx = Math.floor(x / TILE), cy = Math.floor(y / TILE);
  for (let r = 0; r < 12; r++) for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++) {
    if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue;
    if (walkXY(cx + dx, cy + dy)) return { x: (cx + dx + 0.5) * TILE, y: (cy + dy + 0.5) * TILE };
  }
  return { x, y };
}
function exitPoint(b) {
  const bx = b.tx + b.w / 2, by = b.ty + b.h;
  let best = null, bd = 1e9;
  for (let r = 1; r < 6 && !best; r++) {
    for (let j = b.ty - r; j < b.ty + b.h + r; j++) for (let i = b.tx - r; i < b.tx + b.w + r; i++) {
      if (i >= b.tx - r + 1 && i < b.tx + b.w + r - 1 && j >= b.ty - r + 1 && j < b.ty + b.h + r - 1) continue;
      if (!walkXY(i, j)) continue;
      const d = Math.hypot(i + 0.5 - bx, j + 0.5 - by);
      if (d < bd) { bd = d; best = { x: (i + 0.5) * TILE + (Math.random() - 0.5) * 10, y: (j + 0.5) * TILE + (Math.random() - 0.5) * 10 }; }
    }
  }
  return best || { x: b.x, y: b.y + b.h * TILE / 2 + 8 };
}
function entRect(e) {
  if (e.kind === 'tree') return [e.tx * TILE, e.ty * TILE, (e.tx + 1) * TILE, (e.ty + 1) * TILE];
  return [e.tx * TILE, e.ty * TILE, (e.tx + e.w) * TILE, (e.ty + e.h) * TILE];
}
function distRectPt(x, y, r) {
  const dx = Math.max(r[0] - x, 0, x - r[2]), dy = Math.max(r[1] - y, 0, y - r[3]);
  return Math.hypot(dx, dy);
}
function distTo(u, e) {
  if (e.kind === 'u') return Math.hypot(e.x - u.x, e.y - u.y) - e.r;
  return distRectPt(u.x, u.y, entRect(e));
}
function canAfford(team, c) { const R = G.res[team]; return RES.every((r) => (R[r] || 0) >= (c[r] || 0)); }
function pay(team, c, sign) { const R = G.res[team]; for (const r of RES) if (c[r]) R[r] -= c[r] * (sign || 1); }
function computePop() {
  G.pop[0] = G.pop[1] = 0; G.cap[0] = G.cap[1] = 0;
  for (const u of G.units) if (!u.dead) G.pop[u.team] += UNIT[u.type].pop;
  for (const b of G.blds) if (!b.dead && b.built && BLD[b.type].pop) G.cap[b.team] += BLD[b.type].pop;
  G.cap[0] = Math.min(POP_MAX, G.cap[0]); G.cap[1] = Math.min(POP_MAX, G.cap[1]);
}
function canPlace(type, tx, ty, team) {
  const d = BLD[type];
  for (let j = ty; j < ty + d.h; j++) for (let i = tx; i < tx + d.w; i++) {
    if (!inB(i, j)) return false;
    const k = idx(i, j);
    if (G.occ[k] !== 0) return false;
    const t = G.terrain[k]; if (t !== T_GRASS && t !== T_SAND) return false;
    if (team === 0 && !G.explored[k]) return false;
  }
  return true;
}

// ============================================================ поля путей
function getField(g) {
  let f = G.fields.get(g.key);
  if (f && f.ver === G.fieldVer) { f.used = G.time; return f.d; }
  const d = computeField(g.seeds);
  G.fields.set(g.key, { ver: G.fieldVer, d, used: G.time });
  if (G.fields.size > 90) {
    let ok = null, ot = 1e18;
    for (const [k, v] of G.fields) if (v.used < ot) { ot = v.used; ok = k; }
    G.fields.delete(ok);
  }
  return d;
}
function computeField(seeds) {
  const W = G.W, H = G.H, N = W * H;
  const d = new Float64Array(N).fill(1e9), isSeed = new Uint8Array(N), h = new Heap();
  for (const s of seeds) { if (s < 0 || s >= N) continue; d[s] = 0; isSeed[s] = 1; h.push(s, 0); }
  while (h.size) {
    const i = h.pop(), c = h.lastC;
    if (c > d[i]) continue;
    const x = i % W, y = (i - x) / W;
    for (let k = 0; k < 8; k++) {
      const dx = DIRS[k][0], dy = DIRS[k][1];
      const nx = x + dx, ny = y + dy;
      if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
      const ni = ny * W + nx;
      if (!walk(ni)) continue;
      if (dx && dy) { const a = y * W + nx, b = ny * W + x; if (!(walk(a) || isSeed[a]) || !(walk(b) || isSeed[b])) continue; }
      const nc = c + DIRS[k][2];
      if (nc < d[ni]) { d[ni] = nc; h.push(ni, nc); }
    }
  }
  return d;
}
function goalPt(u, x, y, arrive) {
  x = clamp(x, 4, G.W * TILE - 4); y = clamp(y, 4, G.H * TILE - 4);
  let i = idx(Math.floor(x / TILE), Math.floor(y / TILE));
  if (!walk(i)) { const p = nearestWalkable(x, y); i = idx(Math.floor(p.x / TILE), Math.floor(p.y / TILE)); }
  u.goal = { x, y, rect: null, key: 'p' + i, seeds: [i], arrive: arrive || 3, best: 1e9, stuckT: 0 };
}
function goalEnt(u, e, arrive) {
  const tx = e.tx, ty = e.ty, w = e.kind === 'tree' ? 1 : e.w, h = e.kind === 'tree' ? 1 : e.h;
  const seeds = [];
  for (let j = ty; j < ty + h; j++) for (let i = tx; i < tx + w; i++) seeds.push(idx(i, j));
  u.goal = { rect: entRect(e), key: 'r' + tx + ',' + ty + ',' + w + ',' + h, seeds, arrive: arrive == null ? 6 : arrive, best: 1e9, stuckT: 0, ent: e };
}
function lineClear(x0, y0, x1, y1, rect) {
  const L = Math.hypot(x1 - x0, y1 - y0), n = Math.ceil(L / 10);
  for (let s = 1; s <= n; s++) {
    const x = x0 + (x1 - x0) * s / n, y = y0 + (y1 - y0) * s / n;
    if (walkPx(x, y)) continue;
    if (rect && x >= rect[0] - 1 && x <= rect[2] + 1 && y >= rect[1] - 1 && y <= rect[3] + 1) continue;
    return false;
  }
  return true;
}
function moveBy(u, dx, dy) {
  const nx = u.x + dx, ny = u.y + dy;
  if (walkPx(nx, ny)) { u.x = nx; u.y = ny; return true; }
  if (dx && walkPx(nx, u.y)) { u.x = nx; return true; }
  if (dy && walkPx(u.x, ny)) { u.y = ny; return true; }
  return false;
}
// возвращает true, когда цель достигнута (или недостижима)
function steer(u, dt) {
  const g = u.goal; if (!g) return true;
  let gx, gy;
  if (g.rect) { gx = clamp(u.x, g.rect[0], g.rect[2]); gy = clamp(u.y, g.rect[1], g.rect[3]); } else { gx = g.x; gy = g.y; }
  let dx = gx - u.x, dy = gy - u.y;
  const dist = Math.hypot(dx, dy);
  if (dist <= g.arrive + (g.rect ? u.r : 0)) { u.goal = null; return true; }
  let tx = gx, ty = gy, prog = dist;
  if (!(dist < 170 && lineClear(u.x, u.y, gx, gy, g.rect))) {
    const f = getField(g);
    const ux = Math.floor(u.x / TILE), uy = Math.floor(u.y / TILE), i = uy * G.W + ux;
    prog = f[i] * TILE;
    let best = f[i], bi = -1;
    for (let k = 0; k < 8; k++) {
      const ddx = DIRS[k][0], ddy = DIRS[k][1], nx = ux + ddx, ny = uy + ddy;
      if (!inB(nx, ny)) continue;
      const ni = ny * G.W + nx;
      if (f[ni] < best && walk(ni)) {
        if (ddx && ddy && (!walkXY(ux + ddx, uy) || !walkXY(ux, uy + ddy))) continue;
        best = f[ni]; bi = ni;
      }
    }
    if (bi < 0) { if (f[i] >= 1e8) { u.goal = null; return true; } }
    else { tx = (bi % G.W + 0.5) * TILE; ty = (Math.floor(bi / G.W) + 0.5) * TILE; }
  }
  if (prog < g.best - 2) { g.best = prog; g.stuckT = 0; }
  else { g.stuckT += dt; if (g.stuckT > (dist < 40 ? 1.0 : 3)) { u.goal = null; return true; } }
  dx = tx - u.x; dy = ty - u.y;
  const L = Math.hypot(dx, dy) || 1;
  const step = Math.min(UNIT[u.type].spd * dt, L);
  moveBy(u, dx / L * step, dy / L * step);
  if (Math.abs(dx) > 0.5) u.face = dx > 0 ? 1 : -1;
  if (L > 2) u.dir = dirOf(dx, dy);
  u.moving = true; u.anim += dt;
  return false;
}

// ============================================================ пространственный хеш
function rebuildHash() {
  const head = G.uHead; head.fill(-1);
  if (G.uNext.length < G.units.length) G.uNext = new Int32Array(G.units.length * 2);
  const W = G.W;
  for (let k = 0; k < G.units.length; k++) {
    const u = G.units[k];
    const tx = clamp(Math.floor(u.x / TILE), 0, W - 1), ty = clamp(Math.floor(u.y / TILE), 0, G.H - 1);
    const i = ty * W + tx; G.uNext[k] = head[i]; head[i] = k;
  }
}
function findEnemyAt(x, y, team, radius, unitsOnly) {
  const r = Math.ceil(radius / TILE), cx = Math.floor(x / TILE), cy = Math.floor(y / TILE);
  let best = null, bd = radius * radius;
  const x0 = Math.max(0, cx - r), x1 = Math.min(G.W - 1, cx + r), y0 = Math.max(0, cy - r), y1 = Math.min(G.H - 1, cy + r);
  for (let ty = y0; ty <= y1; ty++) for (let tx = x0; tx <= x1; tx++) {
    for (let k = G.uHead[ty * G.W + tx]; k >= 0; k = G.uNext[k]) {
      const v = G.units[k];
      if (v.team === team || v.dead) continue;
      const d2 = (v.x - x) * (v.x - x) + (v.y - y) * (v.y - y);
      if (d2 < bd) { bd = d2; best = v; }
    }
  }
  if (best || unitsOnly) return best;
  let bb = null, bbd = radius;
  for (const b of G.blds) {
    if (b.team === team || b.dead) continue;
    const d = distRectPt(x, y, entRect(b));
    if (d < bbd) { bbd = d; bb = b; }
  }
  return bb;
}

// ============================================================ обновление
const STEP = 1 / 30;
function update(dt) {
  G.time += dt;
  if (G.units.some((u) => u.dead)) G.units = G.units.filter((u) => !u.dead);
  if (G.blds.some((b) => b.dead)) G.blds = G.blds.filter((b) => !b.dead);
  computePop();
  rebuildHash();
  for (let k = 0; k < G.units.length; k++) { const u = G.units[k]; if (!u.dead) updateUnit(u, dt); }
  separate(dt);
  for (const b of G.blds) if (!b.dead) updateBuilding(b, dt);
  updateProj(dt);
  for (const f of G.fx) { f.t += dt; if (f.vx) { f.x += f.vx * dt; f.y += f.vy * dt; } }
  G.fx = G.fx.filter((f) => f.t < f.life);
  for (const d of G.decals) d.t += dt;
  G.decals = G.decals.filter((d) => d.t < d.life);
  for (const m of G.markers) m.t += dt;
  G.markers = G.markers.filter((m) => m.t < 0.8);
  if (G.ping) { G.ping.t -= dt; if (G.ping.t <= 0) G.ping = null; }
  if (G.sel.length && G.sel.some((e) => e.dead)) { G.sel = G.sel.filter((e) => !e.dead); }
  G.fogT -= dt; if (G.fogT <= 0) { G.fogT = 0.25; updateFog(); }
  G.aiT -= dt; if (G.aiT <= 0) { G.aiT = 1; if (G.ai[1]) aiTick(1); if (G.ai[0]) aiTick(0); }
  G.endT -= dt; if (G.endT <= 0) { G.endT = 1; checkEnd(); }
}

function updateUnit(u, dt) {
  const d = UNIT[u.type];
  u.cd -= dt; u.aimT -= dt; u.hitT -= dt; u.moving = false; u.working = false;
  if (u.target && (u.target.dead || (u.target.kind === 'm'))) {
    u.target = null; u.goal = null;
    if (u.cmd === 'amove' && u.amovePt) goalPt(u, u.amovePt.x, u.amovePt.y, 10);
    else if (u.cmd === 'attack') u.cmd = null;
  }
  u.scanT -= dt;
  if (u.scanT <= 0) {
    u.scanT = 0.35 + Math.random() * 0.2;
    if (u.type !== 'peasant' && u.cmd !== 'move') {
      if (!u.target) {
        const e = findEnemyAt(u.x, u.y, u.team, d.sight * TILE, false);
        if (e) { u.target = e; u.goal = null; }
      } else if (u.target.kind === 'b' && u.cmd !== 'attack') {
        const e = findEnemyAt(u.x, u.y, u.team, d.sight * TILE, true);
        if (e) { u.target = e; u.goal = null; }
      }
    }
  }
  if (u.target) doCombat(u, dt);
  else if (u.task) peasantTask(u, dt);
  else if (u.goal && steer(u, dt) && (u.cmd === 'move' || u.cmd === 'amove')) u.cmd = null;
  if (u.moving && !G.auto && G.fx.length < 600 && Math.random() < (u.type === 'cav' ? 0.35 : u.type === 'cannon' ? 0.2 : 0.03)) {
    G.fx.push({ k: 'dust', x: u.x - u.face * 6 + (Math.random() - 0.5) * 6, y: u.y - 1, vx: -u.face * 6, vy: -4, t: 0, life: 0.7 + Math.random() * 0.4, r: u.type === 'peasant' || u.type === 'pike' || u.type === 'musket' ? 1.5 : 2.5 });
  }
}
function dmgMult(a, t) {
  const d = UNIT[a.type];
  if (t.kind === 'b') return d.vsBld || (d.ranged ? 0.35 : 0.6);
  let m = 1;
  if (d.vsCav && UNIT[t.type].cav) m = d.vsCav;
  if (a.type === 'cav' && (t.type === 'musket' || t.type === 'cannon')) m = 1.4;
  if (a.type === 'pike' && t.type === 'cannon') m = 1.5;
  return m;
}
function doCombat(u, dt) {
  const t = u.target, d = UNIT[u.type];
  const dist = distTo(u, t);
  if (dist <= d.range) {
    u.goal = null;
    if (Math.abs(t.x - u.x) > 1) u.face = t.x > u.x ? 1 : -1;
    u.dir = dirOf(t.x - u.x, t.y - u.y);
    u.aimT = 0.4;
    if (u.cd <= 0) { attack(u, t, dist); u.cd = d.cd * (0.85 + Math.random() * 0.3); }
    return;
  }
  if (u.cmd !== 'attack' && dist > d.sight * TILE * 1.7) { u.target = null; u.goal = null; if (u.cmd === 'amove' && u.amovePt) goalPt(u, u.amovePt.x, u.amovePt.y, 10); return; }
  u.chaseT -= dt;
  if (!u.goal || u.chaseT <= 0) {
    u.chaseT = 0.6;
    const ar = Math.max(2, d.range - 8);
    if (t.kind === 'b') goalEnt(u, t, ar); else goalPt(u, t.x, t.y, ar);
  }
  steer(u, dt);
}
function attack(u, t, dist) {
  const d = UNIT[u.type], m = dmgMult(u, t);
  if (u.type === 'musket') {
    const mx = u.x + u.face * 12, my = u.y - 10;
    G.fx.push({ k: 'flash', x: mx, y: my, t: 0, life: 0.12 });
    for (let i = 0; i < 3; i++) G.fx.push({ k: 'smoke', x: mx + u.face * i * 3, y: my, vx: u.face * (8 + Math.random() * 10), vy: -6 - Math.random() * 6, t: 0, life: 1.4 + Math.random(), r: 4 + Math.random() * 3 });
    G.fx.push({ k: 'tracer', x: mx, y: my, x2: t.x, y2: t.kind === 'u' ? t.y - 8 : t.y, t: 0, life: 0.08 });
    sfx('shot', u.x, u.y);
    const hitP = clamp(0.95 - dist / 450, 0.45, 0.95);
    if (Math.random() < hitP) damage(t, d.atk * m, u);
  } else if (u.type === 'cannon') {
    const tx = t.x + (Math.random() - 0.5) * 22, ty = t.y + (Math.random() - 0.5) * 22;
    const sx = u.x + u.face * 14, sy = u.y - 9;
    G.proj.push({ sx, sy, x: sx, y: sy, tx, ty, t: 0, dur: Math.max(0.35, dist / 330), team: u.team, dmg: d.atk, splash: d.splash, src: u, vs: d.vsBld });
    for (let i = 0; i < 5; i++) G.fx.push({ k: 'smoke', x: sx, y: sy, vx: u.face * (10 + Math.random() * 25), vy: -8 - Math.random() * 10, t: 0, life: 1.8 + Math.random(), r: 6 + Math.random() * 5 });
    G.fx.push({ k: 'flash', x: sx, y: sy, t: 0, life: 0.15, big: 1 });
    sfx('cannon', u.x, u.y);
  } else {
    damage(t, d.atk * m * (0.85 + Math.random() * 0.3), u);
    u.hitT = 0.25;
    if (Math.random() < 0.5) sfx('melee', u.x, u.y);
  }
}
function damage(t, amt, src) {
  if (t.dead) return;
  t.hp -= amt;
  if (t.kind === 'u' && src && !src.dead && src.kind === 'u' && !t.target && t.cmd !== 'move') {
    if (t.type !== 'peasant' || !t.task) t.target = src;
  }
  if (t.team === 0 && G.time - G.lastAlert > 15) {
    G.lastAlert = G.time; toast('⚔ Нас атакуют!'); G.ping = { x: t.x, y: t.y, t: 3 };
  }
  if (t.hp <= 0) kill(t, src);
}
function kill(t, src) {
  t.dead = true; t.hp = 0;
  if (src && src.team !== t.team && src.team >= 0) G.stats[src.team].killed++;
  if (t.team >= 0) G.stats[t.team].lost++;
  if (t.kind === 'u') {
    G.decals.push({ k: 'corpse', x: t.x, y: t.y, type: t.type, team: t.team, face: t.face, dir: t.dir || 0, t: 0, life: 40 });
  } else if (t.kind === 'b') {
    for (let j = t.ty; j < t.ty + t.h; j++) for (let i = t.tx; i < t.tx + t.w; i++) G.occ[idx(i, j)] = 0;
    G.fieldVer++; G.miniDirty = true;
    G.decals.push({ k: 'ruin', x: t.tx * TILE, y: t.ty * TILE, w: t.w * TILE, h: t.h * TILE, t: 0, life: 90 });
    for (let i = 0; i < 14; i++) G.fx.push({ k: 'smoke', x: t.x + (Math.random() - 0.5) * t.w * TILE, y: t.y + (Math.random() - 0.5) * t.h * TILE, vx: (Math.random() - 0.5) * 10, vy: -10 - Math.random() * 15, t: 0, life: 2 + Math.random() * 2, r: 8 + Math.random() * 8, dark: 1 });
    G.fx.push({ k: 'boom', x: t.x, y: t.y, t: 0, life: 0.5, r: 40 });
    sfx('cannon', t.x, t.y);
    if (t.team === 0) toast('Разрушено: ' + BLD[t.type].name);
  }
}
function updateProj(dt) {
  for (const p of G.proj) {
    p.t += dt;
    const k = Math.min(1, p.t / p.dur);
    p.x = p.sx + (p.tx - p.sx) * k; p.y = p.sy + (p.ty - p.sy) * k;
    if (p.t >= p.dur) {
      p.done = true;
      G.fx.push({ k: 'boom', x: p.tx, y: p.ty, t: 0, life: 0.4, r: 22 });
      for (let i = 0; i < 4; i++) G.fx.push({ k: 'smoke', x: p.tx, y: p.ty, vx: (Math.random() - 0.5) * 30, vy: -10 - Math.random() * 10, t: 0, life: 1.2, r: 5 + Math.random() * 4, dark: 1 });
      G.decals.push({ k: 'crater', x: p.tx, y: p.ty, t: 0, life: 25 });
      const r = Math.ceil(p.splash / TILE), cx = Math.floor(p.tx / TILE), cy = Math.floor(p.ty / TILE);
      const hit = [];
      for (let ty = cy - r; ty <= cy + r; ty++) for (let tx = cx - r; tx <= cx + r; tx++) {
        if (!inB(tx, ty)) continue;
        for (let q = G.uHead[ty * G.W + tx]; q >= 0; q = G.uNext[q]) { const v = G.units[q]; if (v && v.team !== p.team && !v.dead) hit.push(v); }
      }
      for (const v of hit) { const d = Math.hypot(v.x - p.tx, v.y - p.ty); if (d < p.splash) damage(v, p.dmg * (1 - d / p.splash * 0.6), p.src); }
      for (const b of G.blds) if (b.team !== p.team && !b.dead && distRectPt(p.tx, p.ty, entRect(b)) < 10) damage(b, p.dmg * (p.vs || 1), p.src);
      sfx('impact', p.tx, p.ty);
    }
  }
  G.proj = G.proj.filter((p) => !p.done);
}
function separate(dt) {
  const W = G.W, H = G.H;
  for (let k = 0; k < G.units.length; k++) {
    const u = G.units[k]; if (u.dead) continue;
    const cx = Math.floor(u.x / TILE), cy = Math.floor(u.y / TILE);
    for (let ty = Math.max(0, cy - 1); ty <= Math.min(H - 1, cy + 1); ty++) for (let tx = Math.max(0, cx - 1); tx <= Math.min(W - 1, cx + 1); tx++) {
      for (let q = G.uHead[ty * W + tx]; q >= 0; q = G.uNext[q]) {
        if (q <= k) continue;
        const v = G.units[q]; if (v.dead) continue;
        let dx = v.x - u.x, dy = v.y - u.y; const rr = u.r + v.r;
        const d2 = dx * dx + dy * dy;
        if (d2 >= rr * rr) continue;
        let d = Math.sqrt(d2);
        if (d < 0.01) { dx = Math.random() - 0.5; dy = Math.random() - 0.5; d = Math.hypot(dx, dy); }
        const push = Math.min((rr - d) * 0.5, 60 * dt);
        const nx = dx / d * push, ny = dy / d * push;
        const wu = u.working ? 0.2 : u.moving ? 0.6 : 1, wv = v.working ? 0.2 : v.moving ? 0.6 : 1;
        moveBy(u, -nx * wu, -ny * wu); moveBy(v, nx * wv, ny * wv);
      }
    }
  }
}

// ============================================================ крестьяне
function sourceOf(t) {
  if (t.res === 'wood') return { kind: 'tree', tx: t.tile % G.W, ty: Math.floor(t.tile / G.W) };
  if (t.res === 'food') return t.farm;
  return t.mine;
}
function sourceValid(t, team) {
  if (t.res === 'wood') return G.terrain[t.tile] === T_FOREST && G.wood[t.tile] > 0;
  if (t.res === 'food') return t.farm && !t.farm.dead && t.farm.built && t.farm.team === team;
  return t.mine && !t.mine.dead && t.mine.amount > 0;
}
function farmWorkers(f, except) {
  let n = 0; for (const u of G.units) if (u !== except && !u.dead && u.task && u.task.farm === f) n++;
  return n;
}
function nearestTree(x, y, rad) {
  const cx = Math.floor(x / TILE), cy = Math.floor(y / TILE);
  let best = -1, bd = 1e18;
  for (let ty = Math.max(0, cy - rad); ty <= Math.min(G.H - 1, cy + rad); ty++) for (let tx = Math.max(0, cx - rad); tx <= Math.min(G.W - 1, cx + rad); tx++) {
    const i = ty * G.W + tx;
    if (G.terrain[i] !== T_FOREST || G.wood[i] === 0) continue;
    if (!(walkXY(tx + 1, ty) || walkXY(tx - 1, ty) || walkXY(tx, ty + 1) || walkXY(tx, ty - 1))) continue;
    const d = ((tx - cx) ** 2 + (ty - cy) ** 2) * (1 + hash2(tx, ty, (G.time * 3) | 0) * 0.6);
    if (d < bd) { bd = d; best = i; }
  }
  return best;
}
function nearestMine(x, y, res, rad) {
  let best = null, bd = rad * TILE;
  for (const m of G.mines) { if (m.dead || m.res !== res) continue; const d = Math.hypot(m.x - x, m.y - y); if (d < bd) { bd = d; best = m; } }
  return best;
}
function nearestFarm(u, x, y) {
  let best = null, bd = 1e9;
  for (const b of G.blds) {
    if (b.dead || b.team !== u.team || b.type !== 'farm' || !b.built) continue;
    if (farmWorkers(b, u) >= 6) continue;
    const d = Math.hypot(b.x - x, b.y - y); if (d < bd) { bd = d; best = b; }
  }
  return best;
}
function findNewSource(u) {
  const t = u.task;
  if (t.res === 'wood') { const ox = t.tile >= 0 ? (t.tile % G.W + 0.5) * TILE : u.x, oy = t.tile >= 0 ? (Math.floor(t.tile / G.W) + 0.5) * TILE : u.y; const i = nearestTree(ox, oy, 12); if (i >= 0) { t.tile = i; u.goal = null; return true; } return false; }
  if (t.res === 'food') { const f = nearestFarm(u, u.x, u.y); if (f) { t.farm = f; u.goal = null; return true; } return false; }
  const m = nearestMine(u.x, u.y, t.res, 30); if (m) { t.mine = m; u.goal = null; return true; } return false;
}
function nearestDrop(u) {
  let best = null, bd = 1e9;
  for (const b of G.blds) { if (b.dead || b.team !== u.team || !b.built || !BLD[b.type].drop) continue; const d = distRectPt(u.x, u.y, entRect(b)); if (d < bd) { bd = d; best = b; } }
  return best;
}
function orderGather(u, spec) {
  u.target = null; u.goal = null; u.cmd = null; u.workT = 0;
  if (u.carryType !== spec.res) { u.carry = 0; }
  u.carryType = spec.res;
  u.task = Object.assign({ type: 'gather', tile: -1, mine: null, farm: null }, spec);
  u.phase = u.carry >= CARRY ? 'ret' : 'go';
}
function orderBuild(u, b) { u.target = null; u.goal = null; u.cmd = null; u.task = { type: 'build', b }; }
function deposit(u) {
  if (u.carry > 0 && u.carryType) {
    const mult = G.ai[u.team] ? DIFF[G.ai[u.team].diff].mult : 1;
    const amt = Math.round(u.carry * mult);
    G.res[u.team][u.carryType] += amt; G.stats[u.team].gathered += amt;
  }
  u.carry = 0;
}
function peasantTask(u, dt) {
  const t = u.task;
  if (t.type === 'build') {
    const b = t.b;
    if (b.dead || b.built) { u.task = null; u.goal = null; afterBuild(u, b); return; }
    if (distTo(u, b) <= u.r + 8) {
      u.goal = null; u.working = true; u.anim += dt;
      if (Math.abs(b.x - u.x) > 1) u.face = b.x > u.x ? 1 : -1;
      u.dir = dirOf(b.x - u.x, b.y - u.y);
      const bd = BLD[b.type];
      b.progress += dt; b.hp = Math.min(b.maxhp, b.hp + b.maxhp * 0.9 * dt / bd.time);
      if (b.progress >= bd.time) finishBuilding(b);
    } else { if (!u.goal) goalEnt(u, b, 6); if (steer(u, dt) && distTo(u, b) > u.r + 8) { t.fail = (t.fail || 0) + 1; if (t.fail > 6) u.task = null; } }
    return;
  }
  if (u.phase === 'ret') {
    const dp = nearestDrop(u);
    if (!dp) { u.task = null; u.goal = null; return; }
    if (distTo(u, dp) <= u.r + 8) { deposit(u); u.phase = 'go'; u.goal = null; }
    else { if (!u.goal || u.goal.ent !== dp) goalEnt(u, dp, 6); steer(u, dt); }
    return;
  }
  if (!sourceValid(t, u.team)) {
    if (!findNewSource(u)) { if (u.carry > 0) { u.phase = 'ret'; u.goal = null; } else { u.task = null; u.goal = null; } return; }
  }
  const src = sourceOf(t);
  if (distTo(u, src) <= u.r + 8) {
    u.goal = null; u.working = true; u.anim += dt;
    const sx = (src.tx + (src.kind === 'tree' ? 0.5 : src.w / 2)) * TILE;
    if (Math.abs(sx - u.x) > 1) u.face = sx > u.x ? 1 : -1;
    const sy = (src.ty + (src.kind === 'tree' ? 0.5 : src.h / 2)) * TILE;
    u.dir = dirOf(sx - u.x, sy - u.y);
    u.workT += dt;
    const gt = GTIME[t.res];
    while (u.workT >= gt) {
      u.workT -= gt; u.carry++;
      if (t.res === 'wood') {
        G.wood[t.tile]--;
        if (G.wood[t.tile] === 0) { G.terrain[t.tile] = T_GRASS; markChunksRect(src.tx, src.ty, 1, 1); G.miniDirty = true; G.decals.push({ k: 'stump', x: (src.tx + 0.5) * TILE, y: (src.ty + 0.6) * TILE, t: 0, life: 9999 }); break; }
      } else if (t.res !== 'food') {
        t.mine.amount--;
        if (t.mine.amount <= 0) { exhaustMine(t.mine); break; }
      }
      if (u.carry >= CARRY) break;
    }
    if (u.carry >= CARRY) { u.phase = 'ret'; u.goal = null; }
  } else {
    if (!u.goal) goalEnt(u, src, 6);
    if (steer(u, dt) && distTo(u, src) > u.r + 8) {
      // не дошёл: попробовать другой источник
      t.fail = (t.fail || 0) + 1;
      if (t.fail > 3) { t.fail = 0; if (t.res === 'wood') { G.wood[t.tile] > 0 && (t.skip = t.tile); const i = nearestTree(u.x, u.y, 10); if (i >= 0 && i !== t.tile) t.tile = i; } }
    }
  }
}
function exhaustMine(m) {
  m.dead = true;
  for (let j = m.ty; j < m.ty + 2; j++) for (let i = m.tx; i < m.tx + 2; i++) G.occ[idx(i, j)] = 0;
  G.fieldVer++; G.miniDirty = true;
  G.decals.push({ k: 'pit', x: m.x, y: m.y, t: 0, life: 9999 });
}
function finishBuilding(b) {
  const d = BLD[b.type];
  b.built = true; b.progress = d.time; b.hp = Math.min(b.maxhp, b.hp + b.maxhp * 0.1);
  G.stats[b.team].built++; G.miniDirty = true;
  if (b.team === 0) { toast('Построено: ' + d.name); sfx('done'); if (G.sel.includes(b)) uiDirty = true; }
}
function afterBuild(u, b) {
  if (b.dead) return;
  if (b.type === 'farm' && farmWorkers(b, u) < 6) { orderGather(u, { res: 'food', farm: b }); return; }
  // ещё стройка рядом?
  for (const o of G.blds) if (!o.dead && !o.built && o.team === u.team && Math.hypot(o.x - u.x, o.y - u.y) < 10 * TILE) { orderBuild(u, o); return; }
  if (b.type === 'store' || b.type === 'hall') {
    const i = nearestTree(b.x, b.y, 10); if (i >= 0) { orderGather(u, { res: 'wood', tile: i }); return; }
    const m = nearestMine(b.x, b.y, 'gold', 10) || nearestMine(b.x, b.y, 'stone', 10); if (m) orderGather(u, { res: m.res, mine: m });
  }
}

// ============================================================ здания
function enqueue(b, type) {
  const d = UNIT[type];
  if (b.queue.length >= 6) return false;
  if (!canAfford(b.team, d.cost)) return false;
  pay(b.team, d.cost); b.queue.push(type);
  return true;
}
function updateBuilding(b, dt) {
  if (!b.built) return;
  if (!G.auto && G.fx.length < 600) {
    b.smokeT = (b.smokeT || Math.random()) - dt;
    if (b.smokeT <= 0) {
      const x0 = b.tx * TILE, y0 = b.ty * TILE;
      if (b.type === 'house') { b.smokeT = 0.5; G.fx.push({ k: 'smoke', x: x0 + 47, y: y0 + 1, vx: 4 + Math.random() * 3, vy: -9, t: 0, life: 2.6, r: 2.5, dark: 0 }); }
      else if (b.type === 'foundry') { b.smokeT = 0.3; G.fx.push({ k: 'smoke', x: x0 + 76, y: y0 - 8, vx: 5 + Math.random() * 4, vy: -12, t: 0, life: 3, r: 4, dark: 1 }); }
      else b.smokeT = 2;
      if (b.hp < b.maxhp * 0.55) {
        b.smokeT = Math.min(b.smokeT, 0.25);
        G.fx.push({ k: 'smoke', x: b.x + (Math.random() - 0.5) * b.w * 16, y: b.ty * TILE + 10, vx: (Math.random() - 0.3) * 8, vy: -14, t: 0, life: 2.5, r: 5, dark: 1 });
        G.fx.push({ k: 'ember', x: b.x + (Math.random() - 0.5) * b.w * 16, y: b.ty * TILE + 14, vx: (Math.random() - 0.5) * 14, vy: -24, t: 0, life: 0.9 });
      }
    }
  }
  if (b.queue.length) {
    const type = b.queue[0], d = UNIT[type];
    if (G.pop[b.team] + d.pop <= G.cap[b.team]) {
      b.popBlocked = false;
      b.qt += dt;
      if (b.qt >= d.time) {
        b.qt = 0; b.queue.shift();
        const p = exitPoint(b);
        const u = spawnUnit(type, b.team, p.x, p.y);
        G.pop[b.team] += d.pop; G.stats[b.team].trained++;
        applyRally(u, b);
        if (b.team === 0 && G.sel.includes(b)) uiDirty = true;
      }
    } else {
      if (!b.popBlocked && b.team === 0) toast('Мало места: постройте дома');
      b.popBlocked = true;
    }
  }
  const bd = BLD[b.type];
  if (bd.atk) {
    b.cd -= dt; b.scanT -= dt;
    if (b.target && (b.target.dead || distRectPt(b.target.x, b.target.y, entRect(b)) > bd.range)) b.target = null;
    if (!b.target && b.scanT <= 0) { b.scanT = 0.5; b.target = findEnemyAt(b.x, b.y, b.team, bd.range + b.w * TILE / 2, true); }
    if (b.target && b.cd <= 0) {
      b.cd = bd.cd;
      const mx = b.x, my = b.ty * TILE + 6;
      G.fx.push({ k: 'flash', x: mx, y: my, t: 0, life: 0.1 });
      G.fx.push({ k: 'smoke', x: mx, y: my, vx: (Math.random() - 0.5) * 10, vy: -8, t: 0, life: 1.2, r: 4 });
      G.fx.push({ k: 'tracer', x: mx, y: my, x2: b.target.x, y2: b.target.y - 8, t: 0, life: 0.08 });
      sfx('shot', b.x, b.y);
      if (Math.random() < 0.8) damage(b.target, bd.atk, b);
    }
  }
}
function applyRally(u, b) {
  const r = b.rally;
  if (!r) { goalPt(u, u.x + (Math.random() - 0.5) * 30, u.y + 20 + Math.random() * 20); return; }
  if (u.type === 'peasant' && r.res) {
    if (r.res === 'wood') { const i = sourceValidTile(r.tile) ? r.tile : nearestTree((r.tile % G.W + 0.5) * TILE, (Math.floor(r.tile / G.W) + 0.5) * TILE, 10); if (i >= 0) { orderGather(u, { res: 'wood', tile: i }); return; } }
    else if (r.res === 'food') { if (r.farm && !r.farm.dead) { orderGather(u, { res: 'food', farm: r.farm }); return; } }
    else if (r.mine && !r.mine.dead) { orderGather(u, { res: r.res, mine: r.mine }); return; }
  }
  goalPt(u, r.x + (Math.random() - 0.5) * 24, r.y + (Math.random() - 0.5) * 24);
  u.cmd = 'move';
}
function sourceValidTile(i) { return i >= 0 && G.terrain[i] === T_FOREST && G.wood[i] > 0; }

// ============================================================ туман войны
let fogCv = null, fogCtx = null, fogImg = null;
function updateFog() {
  const W = G.W, H = G.H, vis = G.visible, exp = G.explored;
  vis.fill(0);
  const mark = (x, y, r) => {
    const cx = Math.floor(x / TILE), cy = Math.floor(y / TILE), r2 = r * r;
    for (let dy = -r; dy <= r; dy++) {
      const ty = cy + dy; if (ty < 0 || ty >= H) continue;
      for (let dx = -r; dx <= r; dx++) {
        if (dx * dx + dy * dy > r2) continue;
        const tx = cx + dx; if (tx < 0 || tx >= W) continue;
        const i = ty * W + tx; vis[i] = 1; exp[i] = 1;
      }
    }
  };
  for (const u of G.units) if (u.team === 0) mark(u.x, u.y, UNIT[u.type].sight);
  for (const b of G.blds) if (b.team === 0) mark(b.x, b.y, BLD[b.type].sight + 1);
  if (!fogCv || fogCv.width !== W) {
    fogCv = document.createElement('canvas'); fogCv.width = W; fogCv.height = H;
    fogCtx = fogCv.getContext('2d'); fogImg = fogCtx.createImageData(W, H);
  }
  const data = fogImg.data;
  for (let i = 0; i < W * H; i++) { data[i * 4 + 3] = vis[i] ? 0 : exp[i] ? 110 : 255; }
  fogCtx.putImageData(fogImg, 0, 0);
}
function seen(e) {
  if (e.team === 0 || G.auto) return true;
  const tx = clamp(Math.floor(e.x / TILE), 0, G.W - 1), ty = clamp(Math.floor(e.y / TILE), 0, G.H - 1);
  if (e.kind === 'u') return G.visible[ty * G.W + tx] === 1;
  return G.explored[e.ty * G.W + e.tx] === 1 || G.explored[ty * G.W + tx] === 1;
}

// ============================================================ ИИ
function aiTick(team) {
  const A = G.ai[team], P = DIFF[A.diff], R = G.res[team];
  const units = G.units.filter((u) => u.team === team && !u.dead);
  const peas = units.filter((u) => u.type === 'peasant');
  const army = units.filter((u) => u.type !== 'peasant');
  const myB = G.blds.filter((b) => b.team === team && !b.dead);
  const halls = myB.filter((b) => b.type === 'hall');
  if (halls.length) A.home = { x: halls[0].x, y: halls[0].y };
  if (!A.home) { if (units.length) A.home = { x: units[0].x, y: units[0].y }; else return; }
  const count = (t) => myB.filter((b) => b.type === t).length;
  const pend = myB.filter((b) => !b.built);
  const T = G.time / P.pace;

  // брошенные стройки (недостижимые) — снести
  for (const b of pend) {
    if (b.lastProg !== b.progress) { b.lastProg = b.progress; b.stallT = 0; } else b.stallT = (b.stallT || 0) + 1;
    if (b.stallT > 45) { pay(team, BLD[b.type].cost, -0.5); kill(b, null); }
  }
  // строители для незавершённых зданий
  for (const b of pend) {
    if (b.dead) continue;
    const n = peas.filter((u) => u.task && u.task.type === 'build' && u.task.b === b).length;
    if (n < 2) {
      const cand = peas.filter((u) => !(u.task && u.task.type === 'build')).sort((a, c) => Math.hypot(a.x - b.x, a.y - b.y) - Math.hypot(c.x - b.x, c.y - b.y));
      for (let i = 0; i < Math.min(2 - n, cand.length); i++) orderBuild(cand[i], b);
    }
  }
  // восстановление ратуши
  if (!halls.length && peas.length && canAfford(team, BLD.hall.cost) && !pend.some((b) => b.type === 'hall')) aiBuild(team, 'hall', A);
  // дома
  if (G.cap[team] < POP_MAX && G.cap[team] - G.pop[team] < 6 && !pend.some((b) => b.type === 'house')) aiBuild(team, 'house', A);
  // порядок застройки
  const order = [['farm', 0, 1], ['barracks', 45, 1], ['farm', 90, 2], ['store', 110, 1], ['farm', 170, 3], ['tower', 220, 1], ['stable', 240, 1],
    ['barracks', 340, 2], ['farm', 360, 4], ['foundry', 420, 1], ['tower', 480, 2], ['farm', 520, 5], ['stable', 600, 2]];
  if (pend.length < 2) {
    for (const [type, t, n] of order) {
      if (T >= t && count(type) < n) { if (canAfford(team, BLD[type].cost)) aiBuild(team, type, A); break; }
    }
  }
  // крестьяне
  const queued = halls.reduce((s, h) => s + h.queue.filter((q) => q === 'peasant').length, 0);
  for (const h of halls) if (h.built && peas.length + queued < P.peas && h.queue.length < 2) enqueue(h, 'peasant');
  // распределение работ
  const have = { food: 0, wood: 0, stone: 0, gold: 0 };
  for (const u of peas) if (u.task && u.task.type === 'gather') have[u.task.res]++;
  const late = count('barracks') > 0;
  const ratio = late ? { food: 0.32, wood: 0.38, gold: 0.2, stone: 0.1 } : { food: 0.42, wood: 0.43, gold: 0.08, stone: 0.07 };
  if (R.stone > 500) ratio.stone = 0.02;
  if (R.gold > 900) ratio.gold = 0.05;
  if (R.food > 1200) ratio.food = 0.15;
  if (R.wood < 150) ratio.wood += 0.15;
  for (const u of peas) {
    if (u.task || u.goal || u.target) continue;
    let best = 'wood', bv = -1e9;
    for (const r of RES) { const v = ratio[r] * peas.length - have[r]; if (v > bv) { bv = v; best = r; } }
    if (!aiAssign(u, best, A)) { if (!aiAssign(u, 'wood', A)) aiAssign(u, 'gold', A); }
    if (u.task) have[u.task.res]++;
  }
  // войска
  const cannons = army.filter((u) => u.type === 'cannon').length;
  const reserve = pend.length ? 0 : 1;
  const needHouse = G.cap[team] < POP_MAX && G.cap[team] - G.pop[team] < 6;
  for (const b of myB) {
    if (!b.built || b.queue.length >= 2) continue;
    if (needHouse && R.wood < 120 && b.type !== 'hall') continue;
    if (b.type === 'barracks') {
      const mus = army.filter((u) => u.type === 'musket').length, pk = army.filter((u) => u.type === 'pike').length;
      const want = mus < pk ? 'musket' : 'pike';
      if (!enqueue(b, want)) enqueue(b, want === 'musket' ? 'pike' : 'musket');
    } else if (b.type === 'stable' && reserve) enqueue(b, 'cav');
    else if (b.type === 'foundry' && cannons < P.cannons) enqueue(b, 'cannon');
  }
  // оборона
  const threat = findEnemyAt(A.home.x, A.home.y, team, 15 * TILE, true);
  if (threat) {
    for (const u of army) if (!u.target && u.cmd !== 'amove') { u.target = threat; u.goal = null; }
    if (army.length < 3) for (const u of peas) if (Math.hypot(u.x - threat.x, u.y - threat.y) < 5 * TILE && !u.target) { u.target = threat; }
  }
  // атака
  const idle = army.filter((u) => !u.target && u.cmd !== 'amove');
  const need = P.wave + A.wave * P.grow;
  if ((G.time >= A.nextAttack && idle.length >= need) || (idle.length >= 60 && G.time >= A.nextAttack - P.interval * 0.6)) {
    let tgt = null, bd = 1e18;
    for (const b of G.blds) if (b.team !== team && !b.dead) { const d = Math.hypot(b.x - A.home.x, b.y - A.home.y); if (d < bd) { bd = d; tgt = b; } }
    if (!tgt) for (const u of G.units) if (u.team !== team && !u.dead) { const d = Math.hypot(u.x - A.home.x, u.y - A.home.y); if (d < bd) { bd = d; tgt = u; } }
    if (tgt) {
      groupMove(idle, tgt.x, tgt.y, 'amove');
      A.wave++; A.nextAttack = G.time + P.interval;
      if (team === 1 && !G.auto) toast('Разведка: враг выступил в поход!');
    }
  }
}
function aiAssign(u, res, A) {
  const h = A.home;
  if (res === 'food') { const f = nearestFarm(u, h.x, h.y); if (f) { orderGather(u, { res: 'food', farm: f }); return true; } return false; }
  if (res === 'wood') { const i = nearestTree(h.x, h.y, 22); if (i >= 0) { orderGather(u, { res: 'wood', tile: i }); return true; } return false; }
  const m = nearestMine(h.x, h.y, res, 40); if (m) { orderGather(u, { res, mine: m }); return true; }
  return false;
}
function aiBuild(team, type, A) {
  const d = BLD[type];
  if (!canAfford(team, d.cost)) return null;
  let cx = Math.floor(A.home.x / TILE), cy = Math.floor(A.home.y / TILE);
  if (type === 'store') { const i = nearestTree(A.home.x, A.home.y, 20); if (i >= 0) { cx = i % G.W; cy = Math.floor(i / G.W); } }
  if (type === 'tower') { cx += Math.sign(G.W / 2 - cx) * 5; cy += Math.sign(G.H / 2 - cy) * 5; }
  for (let r = type === 'store' ? 2 : 3; r < 26; r++) {
    const cands = [];
    for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++) {
      if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue;
      const tx = cx + dx - Math.floor(d.w / 2), ty = cy + dy - Math.floor(d.h / 2);
      if (spotOk(type, tx, ty)) cands.push([tx, ty]);
    }
    if (cands.length) {
      const [tx, ty] = cands[Math.floor(Math.random() * cands.length)];
      pay(team, d.cost);
      return placeBuilding(type, team, tx, ty, false);
    }
  }
  return null;
}
function spotOk(type, tx, ty) {
  const d = BLD[type];
  for (let j = ty - 1; j < ty + d.h + 1; j++) for (let i = tx - 1; i < tx + d.w + 1; i++) {
    if (!inB(i, j)) return false;
    const k = idx(i, j);
    const inner = i >= tx && j >= ty && i < tx + d.w && j < ty + d.h;
    if (inner) { if (G.occ[k] || (G.terrain[k] !== T_GRASS && G.terrain[k] !== T_SAND)) return false; }
    else if (!walk(k)) return false;
  }
  return true;
}

// ============================================================ приказы игрока
function groupMove(list, x, y, cmd) {
  if (!list.length) return;
  let cx = 0, cy = 0; for (const u of list) { cx += u.x; cy += u.y; } cx /= list.length; cy /= list.length;
  let dx = x - cx, dy = y - cy; const L = Math.hypot(dx, dy);
  if (L < 1) { dx = 1; dy = 0; } else { dx /= L; dy /= L; }
  const px = -dy, py = dx; // вдоль фронта
  const sorted = list.slice().sort((a, b) => FORM_ORDER[a.type] - FORM_ORDER[b.type]);
  const n = sorted.length;
  const cols = n <= 3 ? n : Math.max(3, Math.ceil(Math.sqrt(n * 2.2)));
  const sp = list.some((u) => u.r > 7) ? 24 : 18;
  const rows = Math.ceil(n / cols);
  for (let k = 0; k < n; k++) {
    const u = sorted[k];
    const row = Math.floor(k / cols), col = k % cols;
    const inRow = row === rows - 1 ? n - row * cols : cols;
    const off = (col - (inRow - 1) / 2) * sp;
    let sx = x + px * off - dx * row * sp, sy = y + py * off - dy * row * sp;
    if (!walkPx(sx, sy)) { const p = nearestWalkable(sx, sy); sx = p.x; sy = p.y; }
    u.task = null; u.target = null; u.cmd = cmd || 'move';
    u.amovePt = cmd === 'amove' ? { x: sx, y: sy } : null;
    goalPt(u, sx, sy, 3);
  }
}
function ownSelUnits() { return G.sel.filter((e) => e.kind === 'u' && e.team === 0 && !e.dead); }
function pickAt(wx, wy) {
  let best = null, bd = 16;
  for (const u of G.units) {
    if (u.dead || !seen(u)) continue;
    const d = Math.hypot(u.x - wx, u.y - 8 - wy);
    if (d < bd + u.r * 0.5) { bd = d; best = u; }
  }
  if (best) return best;
  for (const b of G.blds) { if (b.dead || !seen(b)) continue; const r = entRect(b); if (wx >= r[0] && wx <= r[2] && wy >= r[1] - 14 && wy <= r[3]) return b; }
  for (const m of G.mines) { if (m.dead) continue; const r = entRect(m); if (wx >= r[0] - 4 && wx <= r[2] + 4 && wy >= r[1] - 8 && wy <= r[3] + 4) return G.explored[idx(m.tx, m.ty)] ? m : null; }
  const tx = Math.floor(wx / TILE), ty = Math.floor((wy + 6) / TILE);
  if (inB(tx, ty) && G.terrain[idx(tx, ty)] === T_FOREST && G.explored[idx(tx, ty)]) return { kind: 'tree', tx, ty, tile: idx(tx, ty) };
  return null;
}
let lastTap = { t: 0, id: 0 };
function tapWorld(wx, wy, isMouse) {
  if (G.placing) { setGhost(wx, wy); if (isMouse && G.placing.ok) confirmPlace(); return; }
  const hit = pickAt(wx, wy);
  const sel = ownSelUnits();
  const now = performance.now();
  if (hit && hit.kind === 'u' && hit.team === 0 && !G.amove) {
    if (lastTap.id === hit.id && now - lastTap.t < 350) {
      const v = viewRect();
      G.sel = G.units.filter((u) => u.team === 0 && u.type === hit.type && u.x > v[0] && u.x < v[2] && u.y > v[1] && u.y < v[3]);
    } else G.sel = [hit];
    lastTap = { t: now, id: hit.id }; uiDirty = true; sfx('click'); return;
  }
  if (hit && hit.kind === 'b' && hit.team === 0) {
    const peas = sel.filter((u) => u.type === 'peasant');
    if (peas.length && peas.length === sel.length) {
      if (!hit.built) { for (const u of peas) orderBuild(u, hit); marker(hit.x, hit.y, '#e8b84a'); return; }
      if (hit.type === 'farm') { for (const u of peas) orderGather(u, { res: 'food', farm: hit }); marker(hit.x, hit.y, '#e8b84a'); return; }
      if (BLD[hit.type].drop && peas.some((u) => u.carry > 0)) { for (const u of peas) if (u.carry) { if (!u.task) u.task = { type: 'gather', res: u.carryType, tile: -1, mine: null, farm: null }; u.phase = 'ret'; u.goal = null; } return; }
    }
    if (G.sel.length === 1 && G.sel[0] === hit) { return; }
    G.sel = [hit]; uiDirty = true; sfx('click'); return;
  }
  if (sel.length) {
    if (hit && (hit.kind === 'u' || hit.kind === 'b') && hit.team === 1) {
      for (const u of sel) { u.task = null; u.goal = null; u.target = hit; u.cmd = 'attack'; }
      marker(hit.x, hit.y - 6, '#ff4a3a'); sfx('order'); G.amove = false; uiDirty = true; return;
    }
    const peas = sel.filter((u) => u.type === 'peasant'), rest = sel.filter((u) => u.type !== 'peasant');
    if (hit && peas.length && !G.amove && (hit.kind === 'm' || hit.kind === 'tree')) {
      if (hit.kind === 'm') for (const u of peas) orderGather(u, { res: hit.res, mine: hit });
      else {
        peas.forEach((u, k) => { let i = hit.tile; if (k > 0) { const j = nearestTree(hit.tx * TILE + 16 + (k % 3 - 1) * 40, hit.ty * TILE + 16 + (Math.floor(k / 3) % 3 - 1) * 40, 3); if (j >= 0) i = j; } orderGather(u, { res: 'wood', tile: i }); });
      }
      marker(hit.x || (hit.tx + 0.5) * TILE, hit.y || (hit.ty + 0.5) * TILE, '#e8b84a');
      if (rest.length) groupMove(rest, wx, wy, 'move');
      sfx('order'); return;
    }
    groupMove(sel, wx, wy, G.amove ? 'amove' : 'move');
    marker(wx, wy, G.amove ? '#ff4a3a' : '#7cff6a'); sfx('order');
    if (G.amove) { G.amove = false; uiDirty = true; }
    return;
  }
  // выбрано своё здание — точка сбора
  if (G.sel.length === 1 && G.sel[0].kind === 'b' && G.sel[0].team === 0 && BLD[G.sel[0].type].trains && !hit) {
    G.sel[0].rally = { x: wx, y: wy }; marker(wx, wy, '#e8b84a'); return;
  }
  if (G.sel.length === 1 && G.sel[0].kind === 'b' && G.sel[0].team === 0 && BLD[G.sel[0].type].trains && hit) {
    const r = { x: hit.x || (hit.tx + 0.5) * TILE, y: hit.y || (hit.ty + 0.5) * TILE };
    if (hit.kind === 'tree') { r.res = 'wood'; r.tile = hit.tile; }
    else if (hit.kind === 'm') { r.res = hit.res; r.mine = hit; }
    else if (hit.kind === 'b' && hit.type === 'farm' && hit.team === 0) { r.res = 'food'; r.farm = hit; }
    G.sel[0].rally = r; marker(r.x, r.y, '#e8b84a'); return;
  }
  // просто осмотр
  if (hit && (hit.kind === 'u' || hit.kind === 'b' || hit.kind === 'm')) { G.sel = [hit]; uiDirty = true; return; }
  if (G.sel.length) { G.sel = []; uiDirty = true; }
}
function marker(x, y, col) { G.markers.push({ x, y, col, t: 0 }); }
function boxSelect(x0, y0, x1, y1) {
  const a = Math.min(x0, x1), b = Math.min(y0, y1), c = Math.max(x0, x1), d = Math.max(y0, y1);
  const list = G.units.filter((u) => u.team === 0 && !u.dead && u.x >= a && u.x <= c && u.y - 6 >= b - 6 && u.y - 6 <= d + 6);
  const mil = list.filter((u) => u.type !== 'peasant');
  G.sel = mil.length && mil.length < list.length && mil.length >= 3 ? mil : list;
  uiDirty = true;
  if (G.sel.length) sfx('click');
}
function setGhost(wx, wy) {
  const p = G.placing, d = BLD[p.type];
  p.tx = Math.floor(wx / TILE - d.w / 2 + 0.5); p.ty = Math.floor(wy / TILE - d.h / 2 + 0.5);
  p.ok = canPlace(p.type, p.tx, p.ty, 0);
}
function startPlacing(type) {
  if (!canAfford(0, BLD[type].cost)) { toast('Не хватает: ' + missing(BLD[type].cost)); return; }
  G.placing = { type, tx: 0, ty: 0, ok: false };
  const v = viewRect(); setGhost((v[0] + v[2]) / 2, (v[1] + v[3]) / 2);
  uiDirty = true;
}
function confirmPlace() {
  const p = G.placing; if (!p) return;
  if (!p.ok) { toast('Сюда нельзя: нужно свободное открытое место'); return; }
  const d = BLD[p.type];
  if (!canAfford(0, d.cost)) { toast('Не хватает: ' + missing(d.cost)); return; }
  pay(0, d.cost);
  const b = placeBuilding(p.type, 0, p.tx, p.ty, false);
  for (const u of ownSelUnits()) if (u.type === 'peasant') orderBuild(u, b);
  G.placing = null; uiDirty = true; sfx('order');
}
function missing(c) { const R = G.res[0]; return RES.filter((r) => (c[r] || 0) > R[r]).map((r) => RNAME[r].toLowerCase()).join(', '); }

// ============================================================ конец игры
function checkEnd() {
  if (G.over) return;
  const alive = [0, 1].map((t) => G.blds.some((b) => b.team === t && !b.dead) || G.units.some((u) => u.team === t && !u.dead && u.type !== 'peasant') || G.units.some((u) => u.team === t && !u.dead && u.type === 'peasant' && G.blds.some((b) => b.team === t)));
  const aliveU = [0, 1].map((t) => G.blds.some((b) => b.team === t) || G.units.some((u) => u.team === t));
  if (!aliveU[1] || !alive[1]) endGame(true);
  else if (!aliveU[0] || !alive[0]) endGame(false);
}
function endGame(win) {
  G.over = true; G.winner = win ? 0 : 1;
  if (G.auto) { document.title = 'END:' + G.winner + ':' + Math.round(G.time); return; }
  $('endTitle').textContent = win ? 'Победа!' : 'Поражение';
  const s = G.stats, m = Math.floor(G.time / 60), sec = Math.floor(G.time % 60);
  $('endStats').innerHTML = `<div>Время битвы: ${m}:${String(sec).padStart(2, '0')}</div><table>
  <tr><td></td><td>Вы</td><td>Враг</td></tr>
  <tr><td>Добыто</td><td>${s[0].gathered}</td><td>${s[1].gathered}</td></tr>
  <tr><td>Обучено</td><td>${s[0].trained}</td><td>${s[1].trained}</td></tr>
  <tr><td>Построено</td><td>${s[0].built}</td><td>${s[1].built}</td></tr>
  <tr><td>Уничтожено</td><td>${s[0].killed}</td><td>${s[1].killed}</td></tr>
  <tr><td>Потеряно</td><td>${s[0].lost}</td><td>${s[1].lost}</td></tr></table>`;
  setTimeout(() => $('endm').classList.remove('hidden'), 1200);
  sfx(win ? 'done' : 'cannon');
}

// ============================================================ отрисовка: местность
function markChunksRect(tx, ty, w, h) {
  for (let cy = Math.floor((ty - 1) / CH); cy <= Math.floor((ty + h) / CH); cy++)
    for (let cx = Math.floor((tx - 1) / CH); cx <= Math.floor((tx + w) / CH); cx++) G.chunks.delete(cx + ',' + cy);
  // деревья соседнего снизу чанка заходят вверх
  G.chunks.delete(Math.floor(tx / CH) + ',' + (Math.floor((ty + h) / CH) + 1));
}
function grassCol(x, y) {
  const g = fbm(x / 7, y / 7, G.seed + 50), v = hash2(x, y, 3) * 0.015;
  const r = 96 + g * 30 - v * 60, gg = 138 + g * 24 - v * 40, b = 62 + g * 12;
  return `rgb(${r | 0},${gg | 0},${b | 0})`;
}
function getChunk(cx, cy) {
  const key = cx + ',' + cy;
  let c = G.chunks.get(key);
  if (c) return c;
  c = document.createElement('canvas');
  c.width = CH * TILE + 2; c.height = CH * TILE + CPAD + 2;
  const g = c.getContext('2d');
  g.translate(0, CPAD);
  const x0 = cx * CH, y0 = cy * CH;
  for (let ty = y0; ty < Math.min(G.H, y0 + CH + 1); ty++) for (let tx = x0; tx < Math.min(G.W, x0 + CH + 1); tx++) {
    const t = G.terrain[idx(tx, ty)], px = (tx - x0) * TILE, py = (ty - y0) * TILE;
    if (t === T_WATER) {
      const dpt = fbm(tx / 5, ty / 5, G.seed + 77);
      g.fillStyle = `rgb(${46 + dpt * 20 | 0},${92 + dpt * 30 | 0},${140 + dpt * 25 | 0})`; g.fillRect(px, py, TILE, TILE);
      g.strokeStyle = 'rgba(200,230,255,.25)'; g.lineWidth = 1.2;
      const h = hash2(tx, ty, 5);
      g.beginPath(); g.moveTo(px + 4 + h * 10, py + 10 + h * 12); g.quadraticCurveTo(px + 10 + h * 10, py + 6 + h * 12, px + 16 + h * 10, py + 10 + h * 12); g.stroke();
    } else if (t === T_SAND) {
      g.fillStyle = grassCol(tx, ty); g.fillRect(px, py, TILE, TILE); g.fillStyle = '#cdb67e'; g.beginPath(); g.ellipse(px + 16, py + 16, 21, 21, 0, 0, 7); g.fill();
      g.fillStyle = 'rgba(120,90,40,.25)';
      for (let k = 0; k < 4; k++) g.fillRect(px + hash2(tx, ty, k) * 30, py + hash2(ty, tx, k) * 30, 2, 2);
    } else {
      g.fillStyle = grassCol(tx, ty); g.fillRect(px, py, TILE, TILE);
      g.strokeStyle = 'rgba(40,80,20,.35)'; g.lineWidth = 1;
      for (let k = 0; k < 3; k++) {
        const ax = px + hash2(tx, ty, k + 20) * 28 + 2, ay = py + hash2(tx, ty, k + 40) * 28 + 4;
        g.beginPath(); g.moveTo(ax, ay); g.lineTo(ax - 1.5, ay - 4); g.moveTo(ax, ay); g.lineTo(ax + 1.5, ay - 4); g.stroke();
      }
      if (hash2(tx, ty, 71) < 0.06) { g.fillStyle = 'rgba(120,96,60,.28)'; g.beginPath(); g.ellipse(px + 8 + hash2(tx, ty, 72) * 16, py + 8 + hash2(tx, ty, 73) * 16, 7 + hash2(tx, ty, 74) * 6, 4 + hash2(tx, ty, 75) * 3, 0, 0, 7); g.fill(); }
      if (hash2(tx, ty, 81) < 0.05) { g.fillStyle = '#8d8f88'; g.beginPath(); g.ellipse(px + 6 + hash2(tx, ty, 82) * 20, py + 10 + hash2(tx, ty, 83) * 16, 2.4, 1.6, 0, 0, 7); g.fill(); g.fillStyle = '#b8bab2'; g.fillRect(px + 5 + hash2(tx, ty, 82) * 20, py + 9 + hash2(tx, ty, 83) * 16, 1.5, 1); }
      if (hash2(tx, ty, 91) < 0.035) { g.fillStyle = '#3f6e2a'; g.beginPath(); g.arc(px + 16, py + 18, 5, 0, 7); g.arc(px + 21, py + 20, 4, 0, 7); g.fill(); g.fillStyle = '#5c9440'; g.beginPath(); g.arc(px + 15, py + 16, 2.5, 0, 7); g.fill(); }
      if (hash2(tx, ty, 99) < 0.04) { g.fillStyle = ['#f0e070', '#f4f4f4', '#d070c0'][(hash2(tx, ty, 98) * 3) | 0]; g.fillRect(px + 10, py + 14, 3, 3); g.fillRect(px + 18, py + 20, 3, 3); }
    }
  }
  // деревья, сверху вниз (с соседями по краям, чтобы кроны и тени не резались на стыках)
  if (SPR.ok) {
    for (let ty = Math.max(0, y0 - 1); ty < Math.min(G.H, y0 + CH); ty++) for (let tx = Math.max(0, x0 - 2); tx < Math.min(G.W, x0 + CH + 1); tx++) {
      if (G.terrain[idx(tx, ty)] !== T_FOREST) continue;
      const px = (tx - x0) * TILE, py = (ty - y0) * TILE;
      if (tx >= x0 && tx < x0 + CH && ty >= y0) { g.fillStyle = 'rgba(30,55,20,.28)'; g.beginPath(); g.ellipse(px + 16, py + 18, 20, 16, 0, 0, 7); g.fill(); }
      const n = hash2(tx, ty, 11) < 0.45 ? 2 : 1;
      for (let k = 0; k < n; k++) {
        const ox = px + (n === 1 ? 10 + hash2(tx, ty, 1) * 12 : 4 + k * 16 + hash2(tx, ty, k * 3 + 1) * 8), oy = py + 14 + k * 9 + hash2(tx, ty, k * 3 + 2) * 8;
        const conifer = fbm(tx / 9, ty / 9, G.seed + 31) < 0.5;
        drawTreeSprite(g, ox, oy, (conifer ? 0 : 4) + ((hash2(tx, ty, k * 3 + 3) * 4) | 0));
      }
    }
    G.chunks.set(key, c);
    return c;
  }
  for (let ty = y0; ty < Math.min(G.H, y0 + CH); ty++) for (let tx = x0; tx < Math.min(G.W, x0 + CH); tx++) {
    if (G.terrain[idx(tx, ty)] !== T_FOREST) continue;
    const px = (tx - x0) * TILE, py = (ty - y0) * TILE;
    g.fillStyle = 'rgba(30,60,20,.35)'; g.beginPath(); g.ellipse(px + 16, py + 18, 22, 18, 0, 0, 7); g.fill();
    const n = 2 + (hash2(tx, ty, 11) < 0.4 ? 1 : 0);
    for (let k = 0; k < n; k++) {
      const ox = px + 6 + hash2(tx, ty, k * 3 + 1) * 20, oy = py + 12 + k * 8 + hash2(tx, ty, k * 3 + 2) * 6;
      drawTree(g, ox, oy, hash2(tx, ty, k * 3 + 3));
    }
  }
  G.chunks.set(key, c);
  return c;
}
function drawTree(g, x, y, h) {
  g.fillStyle = 'rgba(0,0,0,.25)'; g.beginPath(); g.ellipse(x + 2, y + 1, 8, 3, 0, 0, 7); g.fill();
  if (h < 0.55) {
    g.fillStyle = '#5a3a1e'; g.fillRect(x - 1.5, y - 6, 3, 7);
    const s = 0.85 + h * 0.4;
    g.fillStyle = '#21471f'; g.beginPath(); g.moveTo(x, y - 30 * s); g.lineTo(x - 9 * s, y - 4); g.lineTo(x + 9 * s, y - 4); g.fill();
    g.fillStyle = '#2f5e2a'; g.beginPath(); g.moveTo(x, y - 30 * s); g.lineTo(x - 7 * s, y - 12 * s); g.lineTo(x + 7 * s, y - 12 * s); g.fill();
    g.fillStyle = '#3c7234'; g.beginPath(); g.moveTo(x, y - 30 * s); g.lineTo(x - 3 * s, y - 20 * s); g.lineTo(x + 2 * s, y - 21 * s); g.fill();
    g.fillStyle = 'rgba(10,30,10,.35)'; g.beginPath(); g.moveTo(x, y - 30 * s); g.lineTo(x + 9 * s, y - 4); g.lineTo(x + 2 * s, y - 4); g.fill();
    g.fillStyle = 'rgba(160,210,120,.25)'; g.beginPath(); g.moveTo(x - 1, y - 26 * s); g.lineTo(x - 6 * s, y - 10 * s); g.lineTo(x - 3 * s, y - 10 * s); g.fill();
  } else {
    g.fillStyle = '#5e3d20'; g.fillRect(x - 2, y - 9, 4, 10);
    const s = 0.8 + (h - 0.55) * 0.8;
    g.fillStyle = '#2d5a26'; g.beginPath(); g.arc(x, y - 16 * s, 10 * s, 0, 7); g.fill();
    g.fillStyle = '#3f7a32'; g.beginPath(); g.arc(x - 3 * s, y - 19 * s, 7 * s, 0, 7); g.fill();
    g.fillStyle = '#56953f'; g.beginPath(); g.arc(x - 4 * s, y - 22 * s, 3.5 * s, 0, 7); g.fill();
    g.fillStyle = 'rgba(10,30,10,.3)'; g.beginPath(); g.arc(x + 4 * s, y - 13 * s, 6 * s, 0, 7); g.fill();
    g.fillStyle = '#3f7a32'; g.beginPath(); g.arc(x + 5 * s, y - 18 * s, 5 * s, 0, 7); g.fill();
    g.fillStyle = 'rgba(200,240,150,.35)'; g.beginPath(); g.arc(x - 5 * s, y - 23 * s, 1.6 * s, 0, 7); g.fill();
  }
}

// ============================================================ пререндеренные 3D-спрайты
const SPR = { ok: false, img: {}, meta: null };
function loadSprites() {
  const D = window.SPR_DATA; if (!D) return;
  SPR.meta = D.meta;
  const keys = Object.keys(D.img); let n = 0;
  for (const k of keys) {
    const im = new Image();
    im.onload = () => { if (++n === keys.length) { SPR.ok = true; if (G) G.chunks.clear(); makeIcons(); uiDirty = true; uiSig = ''; } };
    im.src = D.img[k]; SPR.img[k] = im;
  }
}
// кадры атласа: 0 стоит, 1-4 шаг, 5-7 действие, 8 убит
function unitFrame(e) {
  if (e.working) { const k = Math.floor(e.anim * 5) % 4; return 5 + (k === 3 ? 1 : k); }
  if (e.aimT > 0) {
    if (e.hitT > 0) return e.hitT > 0.17 ? 5 : e.hitT > 0.08 ? 6 : 7;
    if (e.type === 'musket' || e.type === 'cannon') return UNIT[e.type].cd - e.cd < 0.25 ? 6 : 5;
    return 5;
  }
  if (e.moving) return 1 + (Math.floor(e.anim * (e.type === 'cav' ? 9 : 7)) & 3);
  return 0;
}
function drawUnitSprite(g, type, team, dir, f) {
  const m = SPR.meta.units[type], sc = m.scale;
  g.drawImage(SPR.img['u_' + type], f * m.cw * sc, ((team ? 8 : 0) + dir) * m.ch * sc, m.cw * sc, m.ch * sc, -m.ax, -m.ay, m.cw, m.ch);
}
function drawBuildingSprite(g, type, team, x0, y0, prog) {
  const m = SPR.meta.blds[type], d = BLD[type], sc = m.scale, w = d.w * TILE, h = d.h * TILE;
  const cx = x0 + w / 2, cy = y0 + h / 2;
  if (prog < 1) {
    g.fillStyle = 'rgba(138,106,68,.75)'; g.fillRect(x0 + 2, y0 + 2, w - 4, h - 4);
    // здание «растёт» снизу вверх
    const top = cy - m.ay, full = m.ch, vis = Math.max(2, full * (0.15 + 0.85 * prog));
    g.globalAlpha *= 0.9;
    g.drawImage(SPR.img['b_' + type], (team ? m.cw : 0) * sc, (full - vis) * sc, m.cw * sc, vis * sc, cx - m.ax, top + full - vis, m.cw, vis);
    g.globalAlpha /= 0.9;
    g.strokeStyle = '#6a4a28'; g.lineWidth = 2;
    for (let k = 0; k <= d.w * 2; k++) { g.beginPath(); g.moveTo(x0 + 3 + k * (w - 6) / (d.w * 2), y0 + h); g.lineTo(x0 + 3 + k * (w - 6) / (d.w * 2), y0 + 6 - 20 * prog); g.stroke(); }
    for (let yy = y0 + h - 4; yy > y0 + 6 - 20 * prog; yy -= 14) { g.beginPath(); g.moveTo(x0 + 3, yy); g.lineTo(x0 + w - 3, yy); g.stroke(); }
    return;
  }
  g.drawImage(SPR.img['b_' + type], (team ? m.cw : 0) * sc, 0, m.cw * sc, m.ch * sc, cx - m.ax, cy - m.ay, m.cw, m.ch);
}
function drawTreeSprite(g, x, y, k) {
  const m = SPR.meta.trees, sc = m.scale;
  g.drawImage(SPR.img.trees, k * m.cw * sc, 0, m.cw * sc, m.ch * sc, x - m.ax, y - m.ay, m.cw, m.ch);
}
function drawMineSprite(g, mn) {
  const m = SPR.meta.rocks, sc = m.scale, cx = mn.tx * TILE + 32, cy = mn.ty * TILE + 32;
  const s = 0.6 + 0.4 * Math.sqrt(clamp(mn.amount / mn.max, 0, 1));
  g.drawImage(SPR.img.rocks, (mn.res === 'gold' ? 0 : m.cw) * sc, 0, m.cw * sc, m.ch * sc, cx - m.ax * s, cy - m.ay * s, m.cw * s, m.ch * s);
}

// ============================================================ отрисовка: объекты
function drawMine(g, m) {
  const x0 = m.tx * TILE, y0 = m.ty * TILE;
  g.fillStyle = 'rgba(0,0,0,.25)'; g.beginPath(); g.ellipse(x0 + 34, y0 + 52, 30, 10, 0, 0, 7); g.fill();
  const rocks = [[14, 40, 13], [36, 44, 15], [50, 34, 11], [26, 24, 12], [44, 18, 9]];
  const base = m.res === 'gold' ? ['#7d6a52', '#9a8466', '#b8a07c'] : ['#6f747a', '#8d939a', '#aab0b6'];
  for (const [rx, ry, rr] of rocks) {
    g.fillStyle = base[0]; g.beginPath(); g.arc(x0 + rx, y0 + ry, rr, 0, 7); g.fill();
    g.fillStyle = base[1]; g.beginPath(); g.arc(x0 + rx - 2, y0 + ry - 3, rr * 0.75, 0, 7); g.fill();
    g.fillStyle = base[2]; g.beginPath(); g.arc(x0 + rx - 4, y0 + ry - 5, rr * 0.35, 0, 7); g.fill();
  }
  if (m.res === 'gold') {
    g.fillStyle = '#ffd84a';
    for (const [rx, ry] of [[12, 36], [34, 40], [48, 30], [24, 22], [40, 46], [44, 16]]) { g.beginPath(); g.moveTo(x0 + rx, y0 + ry - 3); g.lineTo(x0 + rx + 2.5, y0 + ry); g.lineTo(x0 + rx, y0 + ry + 3); g.lineTo(x0 + rx - 2.5, y0 + ry); g.fill(); }
  } else {
    g.strokeStyle = 'rgba(60,60,60,.6)'; g.lineWidth = 1;
    g.beginPath(); g.moveTo(x0 + 30, y0 + 38); g.lineTo(x0 + 40, y0 + 44); g.moveTo(x0 + 20, y0 + 20); g.lineTo(x0 + 28, y0 + 28); g.stroke();
  }
}
function flag(g, x, y, col, t) {
  g.strokeStyle = '#3a2a1a'; g.lineWidth = 1.5; g.beginPath(); g.moveTo(x, y); g.lineTo(x, y - 20); g.stroke();
  const w = Math.sin((t || 0) * 4 + x) * 1.5;
  g.fillStyle = col; g.beginPath(); g.moveTo(x, y - 20); g.lineTo(x + 12, y - 17 + w); g.lineTo(x, y - 13); g.fill();
}
function drawBuildingShape(g, type, team, x0, y0, prog, t) {
  const c = TEAM[team], d = BLD[type], w = d.w * TILE, h = d.h * TILE;
  g.fillStyle = 'rgba(0,0,0,.28)'; g.fillRect(x0 + 4, y0 + h * 0.35 + 4, w, h * 0.65);
  if (prog < 1) {
    g.fillStyle = '#8a6a44'; g.fillRect(x0 + 2, y0 + 2, w - 4, h - 4);
    g.save(); g.beginPath(); g.rect(x0 - 10, y0 + h * (1 - prog) - 10, w + 20, h * prog + 20); g.clip();
    g.globalAlpha = 0.85;
  }
  const wall = (yy, hh, col, edge) => { g.fillStyle = col; g.fillRect(x0 + 3, yy, w - 6, hh); g.fillStyle = edge; g.fillRect(x0 + 3, yy + hh - 3, w - 6, 3); };
  const roof = (yTop, yBot, col, col2) => {
    g.fillStyle = col; g.beginPath(); g.moveTo(x0 + 1, yBot); g.lineTo(x0 + 10, yTop); g.lineTo(x0 + w - 10, yTop); g.lineTo(x0 + w - 1, yBot); g.fill();
    g.fillStyle = col2; g.fillRect(x0 + 1, yBot - 3, w - 2, 3);
    g.strokeStyle = 'rgba(0,0,0,.15)'; g.lineWidth = 1;
    for (let yy = yTop + 5; yy < yBot - 2; yy += 5) { g.beginPath(); g.moveTo(x0 + 6, yy); g.lineTo(x0 + w - 6, yy); g.stroke(); }
  };
  const win = (x, y) => { g.fillStyle = '#2a2a3a'; g.fillRect(x, y, 6, 7); g.fillStyle = '#e8d8a8'; g.fillRect(x + 2.5, y, 1, 7); g.fillRect(x, y + 3, 6, 1); };
  const door = (x, y, ww, hh) => { g.fillStyle = '#4a2e18'; g.fillRect(x, y, ww, hh); g.fillStyle = '#2a180a'; g.fillRect(x + ww / 2 - 0.5, y, 1, hh); };
  switch (type) {
    case 'house':
      wall(y0 + 28, 32, '#e6dcc2', '#b8aa88');
      g.strokeStyle = '#6a4424'; g.lineWidth = 2; g.strokeRect(x0 + 4, y0 + 29, w - 8, 29); g.beginPath(); g.moveTo(x0 + 4, y0 + 29); g.lineTo(x0 + 18, y0 + 57); g.moveTo(x0 + w - 4, y0 + 29); g.lineTo(x0 + w - 18, y0 + 57); g.stroke();
      roof(y0 + 4, y0 + 30, '#9a4a2a', '#6a2e18'); door(x0 + 26, y0 + 44, 11, 15); win(x0 + 10, y0 + 36); win(x0 + 47, y0 + 36);
      g.fillStyle = '#7a5a4a'; g.fillRect(x0 + 44, y0 + 2, 6, 10);
      break;
    case 'farm': {
      for (let r = 0; r < 6; r++) { g.fillStyle = r % 2 ? '#7a5a30' : '#8a6a38'; g.fillRect(x0 + 2, y0 + 30 + r * 10.5, w - 4, 10.5); g.fillStyle = r % 2 ? '#c8b040' : '#a8c048'; for (let k = 0; k < 9; k++) g.fillRect(x0 + 6 + k * 10, y0 + 32 + r * 10.5, 4, 6); }
      g.fillStyle = '#e0d4b0'; g.fillRect(x0 + 6, y0 + 10, 34, 20); roof(y0 - 2, y0 + 12, '#b88a3a', '#8a6a2a');
      g.save(); g.translate(0, 0); g.restore();
      door(x0 + 18, y0 + 18, 8, 12);
      g.strokeStyle = '#6a4a2a'; g.lineWidth = 1.5; g.strokeRect(x0 + 2, y0 + 30, w - 4, h - 32);
      // мельничка
      g.fillStyle = '#c8b898'; g.fillRect(x0 + 62, y0 + 6, 14, 22); g.fillStyle = '#7a4a2a'; g.beginPath(); g.moveTo(x0 + 60, y0 + 7); g.lineTo(x0 + 69, y0 - 3); g.lineTo(x0 + 78, y0 + 7); g.fill();
      g.save(); g.translate(x0 + 69, y0 + 8); g.rotate((t || 0) * 0.8); g.strokeStyle = '#5a3a1a'; g.lineWidth = 2;
      for (let k = 0; k < 4; k++) { g.rotate(Math.PI / 2); g.beginPath(); g.moveTo(0, 0); g.lineTo(0, -14); g.stroke(); g.fillStyle = '#efe6cc'; g.fillRect(0, -14, 4, 9); }
      g.restore();
      break;
    }
    case 'store':
      wall(y0 + 26, 34, '#9c6b3c', '#6e4a28'); g.strokeStyle = '#6e4a28'; g.lineWidth = 1;
      for (let k = 0; k < 7; k++) { g.beginPath(); g.moveTo(x0 + 8 + k * 8, y0 + 27); g.lineTo(x0 + 8 + k * 8, y0 + 57); g.stroke(); }
      roof(y0 + 4, y0 + 28, '#5e4630', '#3e2e20'); door(x0 + 20, y0 + 38, 24, 22);
      g.fillStyle = '#b08850'; g.fillRect(x0 + 50, y0 + 50, 10, 10); g.strokeStyle = '#6e4a28'; g.strokeRect(x0 + 50, y0 + 50, 10, 10);
      break;
    case 'barracks':
      wall(y0 + 36, 56, '#bdb6a8', '#8a8478');
      g.strokeStyle = 'rgba(80,70,60,.35)'; g.lineWidth = 1;
      for (let yy = y0 + 42; yy < y0 + 90; yy += 7) { g.beginPath(); g.moveTo(x0 + 4, yy); g.lineTo(x0 + w - 4, yy); g.stroke(); }
      roof(y0 + 8, y0 + 40, c.dark, '#222'); door(x0 + 38, y0 + 66, 20, 26);
      win(x0 + 12, y0 + 52); win(x0 + 24, y0 + 52); win(x0 + 66, y0 + 52); win(x0 + 78, y0 + 52);
      g.strokeStyle = '#6a5a4a'; g.lineWidth = 2; g.beginPath(); g.moveTo(x0 + 6, y0 + 92); g.lineTo(x0 + 20, y0 + 70); g.moveTo(x0 + 10, y0 + 92); g.lineTo(x0 + 24, y0 + 70); g.stroke();
      flag(g, x0 + 48, y0 + 12, c.main, t);
      break;
    case 'stable':
      wall(y0 + 38, 54, '#a8784a', '#6e4a28');
      g.strokeStyle = '#6e4a28'; for (let k = 0; k < 11; k++) { g.beginPath(); g.moveTo(x0 + 6 + k * 8.5, y0 + 40); g.lineTo(x0 + 6 + k * 8.5, y0 + 90); g.stroke(); }
      roof(y0 + 10, y0 + 42, '#6b4a2a', '#3e2a18');
      door(x0 + 12, y0 + 64, 18, 28); door(x0 + 39, y0 + 64, 18, 28); door(x0 + 66, y0 + 64, 18, 28);
      g.fillStyle = '#d8c060'; g.beginPath(); g.ellipse(x0 + 86, y0 + 88, 8, 5, 0, 0, 7); g.fill();
      flag(g, x0 + 48, y0 + 14, c.main, t);
      break;
    case 'foundry':
      wall(y0 + 36, 56, '#8b4b3b', '#5e2e22');
      g.strokeStyle = 'rgba(40,20,10,.35)'; g.lineWidth = 1;
      for (let yy = y0 + 42; yy < y0 + 90; yy += 6) { g.beginPath(); g.moveTo(x0 + 4, yy); g.lineTo(x0 + w - 4, yy); g.stroke(); }
      roof(y0 + 12, y0 + 40, '#4a4a50', '#2a2a30');
      g.fillStyle = '#6a3a2a'; g.fillRect(x0 + 70, y0 - 6, 12, 34); g.fillStyle = '#2a1a10'; g.fillRect(x0 + 70, y0 - 8, 12, 4);
      g.fillStyle = '#ff9a3a'; g.fillRect(x0 + 34, y0 + 66, 28, 26); g.fillStyle = '#ffd47a'; g.fillRect(x0 + 40, y0 + 74, 16, 18);
      g.fillStyle = '#7a5a2a'; g.fillRect(x0 + 8, y0 + 84, 20, 4); g.fillStyle = '#3a3a3a'; g.beginPath(); g.arc(x0 + 12, y0 + 88, 4, 0, 7); g.fill();
      flag(g, x0 + 20, y0 + 16, c.main, t);
      break;
    case 'tower':
      g.fillStyle = '#9a948a'; g.fillRect(x0 + 10, y0 + 6, w - 20, h - 8);
      g.fillStyle = '#b8b2a6'; g.fillRect(x0 + 10, y0 + 6, 8, h - 8);
      g.strokeStyle = 'rgba(60,55,50,.4)'; for (let yy = y0 + 12; yy < y0 + h - 2; yy += 7) { g.beginPath(); g.moveTo(x0 + 10, yy); g.lineTo(x0 + w - 10, yy); g.stroke(); }
      g.fillStyle = '#8a847a'; g.fillRect(x0 + 6, y0 + 2, w - 12, 8);
      g.fillStyle = '#9a948a'; for (let k = 0; k < 5; k++) g.fillRect(x0 + 6 + k * 11, y0 - 4, 6, 7);
      g.fillStyle = '#2a2a2a'; g.fillRect(x0 + 28, y0 + 22, 6, 10); g.fillRect(x0 + 28, y0 + 40, 6, 10);
      door(x0 + 25, y0 + 50, 12, 12);
      flag(g, x0 + 32, y0 - 2, c.main, t);
      break;
    case 'hall':
    default:
      wall(y0 + 40, 52, '#d8ccb0', '#a89a7c');
      g.fillStyle = '#c8bca0'; g.fillRect(x0 + 3, y0 + 40, 10, 52); g.fillRect(x0 + w - 13, y0 + 40, 10, 52);
      roof(y0 + 14, y0 + 42, c.dark, '#1a1a1a');
      // башенка
      g.fillStyle = '#e4d8bc'; g.fillRect(x0 + 38, y0 + 2, 20, 30); g.fillStyle = c.dark; g.beginPath(); g.moveTo(x0 + 35, y0 + 4); g.lineTo(x0 + 48, y0 - 22); g.lineTo(x0 + 61, y0 + 4); g.fill();
      g.fillStyle = '#f4e8c0'; g.beginPath(); g.arc(x0 + 48, y0 + 16, 5, 0, 7); g.fill(); g.strokeStyle = '#333'; g.lineWidth = 1; g.beginPath(); g.moveTo(x0 + 48, y0 + 16); g.lineTo(x0 + 48, y0 + 13); g.moveTo(x0 + 48, y0 + 16); g.lineTo(x0 + 50, y0 + 16); g.stroke();
      door(x0 + 38, y0 + 66, 20, 26); g.fillStyle = '#c8bca0'; g.beginPath(); g.arc(x0 + 48, y0 + 66, 10, Math.PI, 0); g.fill(); g.fillStyle = '#4a2e18'; g.beginPath(); g.arc(x0 + 48, y0 + 67, 9, Math.PI, 0); g.fill();
      win(x0 + 14, y0 + 52); win(x0 + 26, y0 + 52); win(x0 + 64, y0 + 52); win(x0 + 76, y0 + 52);
      win(x0 + 14, y0 + 70); win(x0 + 76, y0 + 70);
      flag(g, x0 + 48, y0 - 20, c.main, t);
      break;
  }
  if (prog < 1) {
    g.restore();
    g.strokeStyle = '#6a4a28'; g.lineWidth = 2;
    for (let k = 0; k <= d.w * 2; k++) { g.beginPath(); g.moveTo(x0 + 3 + k * (w - 6) / (d.w * 2), y0 + h); g.lineTo(x0 + 3 + k * (w - 6) / (d.w * 2), y0 + 6); g.stroke(); }
    for (let yy = y0 + h - 4; yy > y0 + 6; yy -= 14) { g.beginPath(); g.moveTo(x0 + 3, yy); g.lineTo(x0 + w - 3, yy); g.stroke(); }
  }
}

function bodyRect(g, x, y, w, h, col) {
  g.fillStyle = col; g.fillRect(x, y, w, h);
  g.fillStyle = 'rgba(255,255,255,.18)'; g.fillRect(x, y, w * 0.35, h);
  g.fillStyle = 'rgba(0,0,0,.22)'; g.fillRect(x + w * 0.68, y, w * 0.32, h);
  g.strokeStyle = 'rgba(20,12,6,.55)'; g.lineWidth = 0.8; g.strokeRect(x, y, w, h);
}
function drawUnitShape(g, type, team, face, anim, moving, working, carry, aim, hit) {
  const c = TEAM[team];
  g.save();
  if (moving) g.translate(0, -Math.abs(Math.sin(anim * 11)) * 0.8);
  if (face < 0) g.scale(-1, 1);
  const s = moving ? Math.sin(anim * 11) * 2.2 : 0;
  g.fillStyle = 'rgba(0,0,0,.28)'; g.beginPath(); g.ellipse(0, 0, type === 'cav' || type === 'cannon' ? 11 : 6, 2.6, 0, 0, 7); g.fill();
  const legs = (col) => { g.strokeStyle = col; g.lineWidth = 2.2; g.beginPath(); g.moveTo(-1.5, -6); g.lineTo(-1.5 - s, 0); g.moveTo(1.5, -6); g.lineTo(1.5 + s, 0); g.stroke(); };
  const head = (y) => { g.fillStyle = '#ecc49a'; g.beginPath(); g.arc(0.5, y, 2.6, 0, 7); g.fill(); g.strokeStyle = 'rgba(60,30,10,.5)'; g.lineWidth = 0.6; g.stroke(); g.fillStyle = 'rgba(255,240,220,.6)'; g.beginPath(); g.arc(-0.3, y - 0.8, 0.9, 0, 7); g.fill(); };
  switch (type) {
    case 'peasant': {
      legs('#5a4630');
      bodyRect(g, -3, -13, 6.5, 8, '#9a7a4e');
      g.fillStyle = c.main; g.fillRect(-3, -8, 6.5, 1.6);
      head(-15.5);
      g.fillStyle = '#d8b45a'; g.beginPath(); g.ellipse(0.5, -17.5, 4.6, 1.5, 0, 0, 7); g.fill(); g.fillRect(-1.5, -20, 4, 3);
      if (working) {
        const a = Math.sin(anim * 9) * 0.9 - 0.2;
        g.save(); g.translate(3, -11); g.rotate(a); g.strokeStyle = '#6a4a2a'; g.lineWidth = 1.6; g.beginPath(); g.moveTo(0, 0); g.lineTo(9, -3); g.stroke();
        g.fillStyle = '#9aa0a8'; g.fillRect(8, -6, 2.5, 6); g.restore();
      }
      if (carry) { g.fillStyle = { food: '#e0c050', wood: '#8a5a2a', stone: '#9aa0a8', gold: '#ffd84a' }[carry]; g.fillRect(-7, -14, 4.5, 6); }
      break;
    }
    case 'pike': {
      legs('#3a3a42');
      bodyRect(g, -3.5, -14, 7.5, 9, c.main);
      g.fillStyle = '#c8c8d0'; g.fillRect(-3.5, -14, 7.5, 3.5); g.fillStyle = '#eef0f4'; g.fillRect(-3, -13.6, 2, 1.2);
      head(-16.5);
      g.fillStyle = '#9aa0aa'; g.beginPath(); g.arc(0.5, -18, 3.4, Math.PI, 0); g.fill(); g.fillRect(-3.5, -18.4, 8, 1.4);
      g.strokeStyle = '#6a4a2a'; g.lineWidth = 1.6; g.beginPath();
      if (aim > 0) { const j = hit > 0 ? 4 : 0; g.moveTo(-4, -10); g.lineTo(20 + j, -11); g.stroke(); g.fillStyle = '#d8dce0'; g.beginPath(); g.moveTo(20 + j, -12.5); g.lineTo(25 + j, -11); g.lineTo(20 + j, -9.5); g.fill(); }
      else { g.moveTo(3, 0); g.lineTo(6, -34); g.stroke(); g.fillStyle = '#d8dce0'; g.beginPath(); g.moveTo(4.8, -34); g.lineTo(6.3, -39); g.lineTo(7.4, -34); g.fill(); }
      break;
    }
    case 'musket': {
      legs('#3a3a42');
      bodyRect(g, -3.5, -14, 7.5, 10, c.main);
      g.fillStyle = c.light; g.fillRect(-0.3, -14, 1.2, 9);
      g.fillStyle = '#e8dcc0'; g.fillRect(-3.5, -7, 7.5, 1.4);
      head(-16.5);
      g.fillStyle = '#1e1e22'; g.beginPath(); g.moveTo(-4.5, -18); g.lineTo(0.5, -22); g.lineTo(5.5, -18); g.lineTo(0.5, -19); g.fill();
      g.strokeStyle = '#4a3420'; g.lineWidth = 1.8; g.beginPath();
      if (aim > 0) { g.moveTo(-1, -11); g.lineTo(14, -12); g.stroke(); g.strokeStyle = '#555'; g.lineWidth = 1.2; g.beginPath(); g.moveTo(8, -12); g.lineTo(15, -12.2); g.stroke(); }
      else { g.moveTo(-3, -4); g.lineTo(3, -24); g.stroke(); }
      break;
    }
    case 'cav': {
      const ls = moving ? Math.sin(anim * 14) * 4 : 0;
      g.strokeStyle = '#3a2414'; g.lineWidth = 2;
      g.beginPath(); g.moveTo(-6, -7); g.lineTo(-6 + ls, 0); g.moveTo(-3, -7); g.lineTo(-3 - ls, 0); g.moveTo(5, -7); g.lineTo(5 - ls, 0); g.moveTo(8, -7); g.lineTo(8 + ls, 0); g.stroke();
      g.fillStyle = '#5a3a22'; g.beginPath(); g.ellipse(1, -10, 9.5, 4.6, 0, 0, 7); g.fill();
      g.strokeStyle = 'rgba(20,10,4,.6)'; g.lineWidth = 0.8; g.stroke();
      g.fillStyle = 'rgba(255,220,180,.18)'; g.beginPath(); g.ellipse(-1, -12, 6, 1.8, 0, 0, 7); g.fill();
      g.fillStyle = '#5a3a22';
      g.beginPath(); g.moveTo(7, -12); g.lineTo(12, -19); g.lineTo(15, -17); g.lineTo(10, -9); g.fill();
      g.beginPath(); g.ellipse(14, -17.5, 3.2, 2, 0.5, 0, 7); g.fill();
      g.strokeStyle = '#2a1a0e'; g.lineWidth = 2; g.beginPath(); g.moveTo(-8, -11); g.lineTo(-12, -5); g.stroke();
      g.fillStyle = c.main; g.fillRect(-4, -12, 9, 2.5);
      bodyRect(g, -2.5, -21, 6.5, 8, '#b8bcc4');
      g.fillStyle = c.main; g.fillRect(-2.5, -15, 6.5, 2);
      head(-23);
      g.fillStyle = '#8a8e96'; g.beginPath(); g.arc(1, -24.5, 3.4, Math.PI, 0); g.fill();
      g.fillStyle = c.light; g.fillRect(-2, -29, 1.4, 4);
      g.strokeStyle = '#d0d4da'; g.lineWidth = 1.4; g.beginPath();
      if (aim > 0 && hit > 0) { g.moveTo(4, -18); g.lineTo(16, -14); } else { g.moveTo(4, -17); g.lineTo(9, -29); }
      g.stroke();
      break;
    }
    case 'cannon': {
      g.fillStyle = '#5a3a1e'; g.fillRect(-12, -7, 18, 4);
      g.save(); g.translate(2, -9); g.rotate(aim > 0 ? -0.12 : -0.05);
      g.fillStyle = '#9a7a3a'; g.fillRect(-12, -3, 24, 6); g.fillStyle = '#b8984a'; g.fillRect(-12, -3, 24, 2);
      g.fillStyle = '#6a5228'; g.fillRect(11, -3.5, 3, 7); g.fillStyle = '#222'; g.fillRect(13.5, -2, 1, 4);
      g.restore();
      g.strokeStyle = '#3a2410'; g.lineWidth = 2; g.fillStyle = '#6a4a2a';
      g.beginPath(); g.arc(-1, -5, 5.5, 0, 7); g.fill(); g.stroke();
      g.strokeStyle = '#3a2410'; g.lineWidth = 1; g.beginPath(); g.moveTo(-6.5, -5); g.lineTo(4.5, -5); g.moveTo(-1, -10.5); g.lineTo(-1, 0.5); g.stroke();
      g.fillStyle = c.main; g.fillRect(-12, -8, 5, 3);
      break;
    }
  }
  g.restore();
}

// ============================================================ кадр
function viewRect() { const z = G.cam.z; return [G.cam.x, G.cam.y, G.cam.x + VW / z, G.cam.y + VH / z]; }
function clampCam() {
  const z = G.cam.z, vw = VW / z, vh = VH / z, MW = G.W * TILE, MH = G.H * TILE;
  G.cam.x = vw >= MW ? (MW - vw) / 2 : clamp(G.cam.x, -60 / z, MW - vw + 60 / z);
  G.cam.y = vh >= MH ? (MH - vh) / 2 : clamp(G.cam.y, -50 / z, MH - vh + 110 / z);
}
function centerCam(x, y) { G.cam.x = x - VW / G.cam.z / 2; G.cam.y = y - VH / G.cam.z / 2; clampCam(); }

function render(now) {
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.fillStyle = '#0b0805'; ctx.fillRect(0, 0, cv.width, cv.height);
  if (!G) return;
  const z = G.cam.z, t = G.time;
  ctx.setTransform(DPR * z, 0, 0, DPR * z, -G.cam.x * DPR * z, -G.cam.y * DPR * z);
  ctx.imageSmoothingEnabled = true;
  const v = viewRect();
  const cs = CH * TILE;
  for (let cy = Math.max(0, Math.floor(v[1] / cs)); cy <= Math.min(Math.ceil(G.H / CH) - 1, Math.floor((v[3] + CPAD) / cs)); cy++)
    for (let cx = Math.max(0, Math.floor(v[0] / cs)); cx <= Math.min(Math.ceil(G.W / CH) - 1, Math.floor(v[2] / cs)); cx++)
      ctx.drawImage(getChunk(cx, cy), cx * cs, cy * cs - CPAD);
  // декали
  for (const d of G.decals) {
    if (d.x < v[0] - 100 || d.x > v[2] + 100 || d.y < v[1] - 100 || d.y > v[3] + 100) continue;
    const a = Math.min(1, (d.life - d.t) / 5);
    ctx.globalAlpha = a;
    if (d.k === 'corpse' && SPR.ok) { ctx.save(); ctx.translate(d.x, d.y); if (d.t < 0.3) { drawUnitSprite(ctx, d.type, d.team, d.dir, 0); } else { ctx.globalAlpha = a * 0.9; drawUnitSprite(ctx, d.type, d.team, d.dir, 8); } ctx.restore(); }
    else if (d.k === 'corpse') { ctx.save(); ctx.translate(d.x, d.y); ctx.rotate((d.face > 0 ? -1.4 : 1.4) * Math.min(1, d.t / 0.35)); ctx.globalAlpha = a * 0.8; drawUnitShape(ctx, d.type, d.team, d.face, 0, false, false, null, 0, 0); ctx.restore(); }
    else if (d.k === 'ruin') { ctx.fillStyle = '#4a3a2a'; ctx.fillRect(d.x + 4, d.y + d.h * 0.3, d.w - 8, d.h * 0.66); ctx.fillStyle = '#2a2018'; for (let k = 0; k < 8; k++) ctx.fillRect(d.x + 6 + hash2(k, d.x, 1) * (d.w - 18), d.y + d.h * 0.35 + hash2(k, d.y, 2) * d.h * 0.5, 10, 6); }
    else if (d.k === 'crater') { ctx.fillStyle = 'rgba(40,30,20,.45)'; ctx.beginPath(); ctx.ellipse(d.x, d.y, 9, 5, 0, 0, 7); ctx.fill(); }
    else if (d.k === 'stump') { ctx.globalAlpha = 1; ctx.fillStyle = '#6a4a2a'; ctx.beginPath(); ctx.ellipse(d.x, d.y, 3.5, 2, 0, 0, 7); ctx.fill(); ctx.fillStyle = '#c8a070'; ctx.beginPath(); ctx.ellipse(d.x, d.y - 1, 2.5, 1.3, 0, 0, 7); ctx.fill(); }
    else if (d.k === 'pit') { ctx.globalAlpha = 1; ctx.fillStyle = 'rgba(70,60,50,.5)'; ctx.beginPath(); ctx.ellipse(d.x, d.y, 24, 14, 0, 0, 7); ctx.fill(); }
  }
  ctx.globalAlpha = 1;
  drawWater(v, t);
  // площадки и тени зданий
  for (const b of G.blds) {
    if (!seen(b) || b.x < v[0] - 160 || b.x > v[2] + 160 || b.y < v[1] - 160 || b.y > v[3] + 160) continue;
    const x0 = b.tx * TILE, y0 = b.ty * TILE, w = b.w * TILE, h = b.h * TILE;
    ctx.fillStyle = 'rgba(120,92,58,.38)'; ctx.beginPath(); ctx.ellipse(x0 + w / 2, y0 + h * 0.62, w * 0.66, h * 0.5, 0, 0, 7); ctx.fill();
    if (b.type !== 'farm' && !SPR.ok) {
      const sh = b.type === 'tower' ? 34 : b.type === 'hall' ? 30 : 22;
      ctx.fillStyle = 'rgba(10,20,10,.26)'; ctx.beginPath();
      ctx.moveTo(x0 + 4, y0 + h); ctx.lineTo(x0 + 4 + sh, y0 + h + sh * 0.35); ctx.lineTo(x0 + w + sh, y0 + h + sh * 0.35);
      ctx.lineTo(x0 + w + sh, y0 + h * 0.3 + sh * 0.35); ctx.lineTo(x0 + w - 2, y0 + h * 0.25); ctx.closePath(); ctx.fill();
    }
  }
  // список для сортировки по глубине
  const list = [];
  const inView = (x, y, m) => x > v[0] - m && x < v[2] + m && y > v[1] - m && y < v[3] + m + 40;
  for (const m of G.mines) if (!m.dead && inView(m.x, m.y, 80) && G.explored[idx(m.tx, m.ty)]) list.push([m.y + 32, m]);
  for (const b of G.blds) if (inView(b.x, b.y, 120) && seen(b)) list.push([(b.ty + b.h) * TILE, b]);
  for (const u of G.units) if (inView(u.x, u.y, 40) && seen(u)) list.push([u.y, u]);
  list.sort((a, b) => a[0] - b[0]);
  const selSet = new Set(G.sel);
  for (const [, e] of list) {
    if (e.kind === 'm') { if (SPR.ok) drawMineSprite(ctx, e); else drawMine(ctx, e); if (selSet.has(e)) { ctx.strokeStyle = '#ffe080'; ctx.lineWidth = 1.5; ctx.strokeRect(e.tx * TILE, e.ty * TILE, 64, 64); } continue; }
    if (e.kind === 'b') {
      if (selSet.has(e)) { ctx.strokeStyle = e.team === 0 ? '#7cff6a' : '#ff6a5a'; ctx.lineWidth = 2; ctx.beginPath(); ctx.ellipse(e.x, e.y + e.h * TILE * 0.2, e.w * TILE * 0.62, e.h * TILE * 0.42, 0, 0, 7); ctx.stroke(); }
      if (SPR.ok) drawBuildingSprite(ctx, e.type, e.team, e.tx * TILE, e.ty * TILE, e.built ? 1 : e.progress / BLD[e.type].time);
      else drawBuildingShape(ctx, e.type, e.team, e.tx * TILE, e.ty * TILE, e.built ? 1 : e.progress / BLD[e.type].time, t);
      if (e.built && e.hp < e.maxhp * 0.55) drawFire(e, t);
      continue;
    }
    if (selSet.has(e)) { const sc = e.team === 0 ? '124,255,106' : '255,106,90'; ctx.fillStyle = `rgba(${sc},.18)`; ctx.strokeStyle = `rgba(${sc},.95)`; ctx.lineWidth = 1.4; ctx.beginPath(); ctx.ellipse(e.x, e.y, e.r + 3, (e.r + 3) * 0.45, 0, 0, 7); ctx.fill(); ctx.stroke(); }
    ctx.save(); ctx.translate(e.x, e.y);
    if (SPR.ok) {
      drawUnitSprite(ctx, e.type, e.team, e.dir, unitFrame(e));
      if (e.carry > 0 && !e.working) { ctx.fillStyle = { food: '#e0c050', wood: '#8a5a2a', stone: '#9aa0a8', gold: '#ffd84a' }[e.carryType]; ctx.strokeStyle = 'rgba(30,20,10,.7)'; ctx.lineWidth = 0.8; ctx.fillRect(-7, -15, 5, 6); ctx.strokeRect(-7, -15, 5, 6); }
    } else drawUnitShape(ctx, e.type, e.team, e.face, e.anim, e.moving, e.working, e.carry > 0 ? e.carryType : null, e.aimT, e.hitT);
    ctx.restore();
  }
  // полоски здоровья
  for (const [, e] of list) {
    if (e.kind === 'm') continue;
    const sel = selSet.has(e);
    if (!sel && e.hp >= e.maxhp && (e.kind !== 'b' || e.built)) continue;
    let w, x, y;
    if (e.kind === 'b') { w = e.w * TILE * 0.7; x = e.x - w / 2; y = e.ty * TILE - 12; }
    else { w = 16; x = e.x - 8; y = e.y - (e.type === 'cav' ? 38 : e.type === 'cannon' ? 24 : 30); }
    ctx.fillStyle = 'rgba(0,0,0,.6)'; ctx.fillRect(x - 1, y - 1, w + 2, 4);
    const f = clamp(e.hp / e.maxhp, 0, 1);
    ctx.fillStyle = f > 0.5 ? '#5c3' : f > 0.25 ? '#ec3' : '#e43'; ctx.fillRect(x, y, w * f, 2);
    if (e.kind === 'b' && !e.built) { ctx.fillStyle = '#e8b84a'; ctx.fillRect(x, y + 4, w * e.progress / BLD[e.type].time, 2); }
  }
  // снаряды
  for (const p of G.proj) {
    const k = Math.min(1, p.t / p.dur), L = Math.hypot(p.tx - p.sx, p.ty - p.sy), hgt = Math.sin(k * Math.PI) * L * 0.18;
    ctx.fillStyle = 'rgba(0,0,0,.25)'; ctx.beginPath(); ctx.ellipse(p.x, p.y, 3, 1.5, 0, 0, 7); ctx.fill();
    ctx.fillStyle = '#1a1a1a'; ctx.beginPath(); ctx.arc(p.x, p.y - hgt, 3, 0, 7); ctx.fill();
  }
  // эффекты
  for (const f of G.fx) {
    const k = f.t / f.life;
    if (f.k === 'smoke') { ctx.fillStyle = f.dark ? `rgba(60,55,50,${0.55 * (1 - k)})` : `rgba(235,235,230,${0.6 * (1 - k)})`; ctx.beginPath(); ctx.arc(f.x, f.y, f.r * (1 + k * 1.6), 0, 7); ctx.fill(); }
    else if (f.k === 'flash') { ctx.fillStyle = `rgba(255,220,120,${1 - k})`; ctx.beginPath(); ctx.arc(f.x, f.y, (f.big ? 8 : 4) * (1 - k * 0.5), 0, 7); ctx.fill(); }
    else if (f.k === 'tracer') { ctx.strokeStyle = `rgba(255,240,200,${0.7 * (1 - k)})`; ctx.lineWidth = 0.8; ctx.beginPath(); ctx.moveTo(f.x, f.y); ctx.lineTo(f.x2, f.y2); ctx.stroke(); }
    else if (f.k === 'dust') { ctx.fillStyle = `rgba(150,122,82,${0.45 * (1 - k)})`; ctx.beginPath(); ctx.arc(f.x, f.y, f.r * (1 + k * 1.4), 0, 7); ctx.fill(); }
    else if (f.k === 'ember') { ctx.fillStyle = `rgba(255,${180 - k * 120 | 0},60,${1 - k})`; ctx.fillRect(f.x, f.y, 1.6, 1.6); }
    else if (f.k === 'boom') { ctx.fillStyle = `rgba(255,${160 - k * 100 | 0},40,${0.85 * (1 - k)})`; ctx.beginPath(); ctx.arc(f.x, f.y - 4, f.r * (0.4 + k * 0.6), 0, 7); ctx.fill(); }
  }
  // тени облаков
  drawClouds(t);
  // туман
  if (!G.auto && fogCv) { ctx.imageSmoothingEnabled = true; ctx.drawImage(fogCv, 0, 0, G.W, G.H, 0, 0, G.W * TILE, G.H * TILE); }
  // маркеры и точки сбора
  for (const m of G.markers) { const k = m.t / 0.8; ctx.strokeStyle = m.col; ctx.globalAlpha = 1 - k; ctx.lineWidth = 2; ctx.beginPath(); ctx.ellipse(m.x, m.y, 4 + k * 12, (4 + k * 12) * 0.5, 0, 0, 7); ctx.stroke(); }
  ctx.globalAlpha = 1;
  if (G.sel.length === 1 && G.sel[0].kind === 'b' && G.sel[0].rally) { const r = G.sel[0].rally; ctx.setLineDash([4, 4]); ctx.strokeStyle = 'rgba(255,230,140,.7)'; ctx.lineWidth = 1.2; ctx.beginPath(); ctx.moveTo(G.sel[0].x, G.sel[0].y); ctx.lineTo(r.x, r.y); ctx.stroke(); ctx.setLineDash([]); flag(ctx, r.x, r.y, TEAM[0].main, t); }
  // призрак здания
  if (G.placing) {
    const p = G.placing, d = BLD[p.type];
    ctx.globalAlpha = 0.6; if (SPR.ok) drawBuildingSprite(ctx, p.type, 0, p.tx * TILE, p.ty * TILE, 1); else drawBuildingShape(ctx, p.type, 0, p.tx * TILE, p.ty * TILE, 1, t); ctx.globalAlpha = 1;
    ctx.fillStyle = p.ok ? 'rgba(80,255,80,.22)' : 'rgba(255,60,40,.3)'; ctx.fillRect(p.tx * TILE, p.ty * TILE, d.w * TILE, d.h * TILE);
    ctx.strokeStyle = p.ok ? '#7cff6a' : '#ff5040'; ctx.lineWidth = 2; ctx.strokeRect(p.tx * TILE, p.ty * TILE, d.w * TILE, d.h * TILE);
  }
  if (G.ping) { const k = 1 - (G.ping.t % 1); ctx.strokeStyle = `rgba(255,60,40,${1 - k})`; ctx.lineWidth = 3; ctx.beginPath(); ctx.arc(G.ping.x, G.ping.y, 20 + k * 40, 0, 7); ctx.stroke(); }
  // рамка
  ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  drawVignette();
  if (gest && gest.mode === 'box' && gest.moved) {
    ctx.strokeStyle = '#7cff6a'; ctx.lineWidth = 1.5; ctx.fillStyle = 'rgba(124,255,106,.12)';
    const x = Math.min(gest.sx, gest.lx), y = Math.min(gest.sy, gest.ly), w = Math.abs(gest.lx - gest.sx), h = Math.abs(gest.ly - gest.sy);
    ctx.fillRect(x, y, w, h); ctx.strokeRect(x, y, w, h);
  }
}


// ============================================================ атмосфера
function drawWater(v, t) {
  const x0 = Math.max(0, Math.floor(v[0] / TILE)), x1 = Math.min(G.W - 1, Math.floor(v[2] / TILE));
  const y0 = Math.max(0, Math.floor(v[1] / TILE)), y1 = Math.min(G.H - 1, Math.floor(v[3] / TILE));
  for (let ty = y0; ty <= y1; ty++) for (let tx = x0; tx <= x1; tx++) {
    if (G.terrain[ty * G.W + tx] !== T_WATER) continue;
    const px = tx * TILE, py = ty * TILE, h = hash2(tx, ty, 7);
    const a = 0.5 + 0.5 * Math.sin(t * 1.6 + h * 12);
    ctx.fillStyle = `rgba(220,240,255,${0.22 * a})`;
    ctx.fillRect(px + 4 + h * 18, py + 6 + ((h * 37) % 1) * 18, 6, 1.2);
    ctx.fillRect(px + 14 - h * 8, py + 20 - ((h * 53) % 1) * 10, 4, 1);
    // пена у берега
    ctx.strokeStyle = `rgba(240,250,255,${0.25 + 0.2 * Math.sin(t * 2 + h * 6)})`; ctx.lineWidth = 1.5;
    const land = (dx, dy) => inB(tx + dx, ty + dy) && G.terrain[(ty + dy) * G.W + tx + dx] !== T_WATER;
    const o = 1.5 + Math.sin(t * 1.8 + h * 5);
    ctx.beginPath();
    if (land(0, -1)) { ctx.moveTo(px, py + o); ctx.lineTo(px + TILE, py + o); }
    if (land(0, 1)) { ctx.moveTo(px, py + TILE - o); ctx.lineTo(px + TILE, py + TILE - o); }
    if (land(-1, 0)) { ctx.moveTo(px + o, py); ctx.lineTo(px + o, py + TILE); }
    if (land(1, 0)) { ctx.moveTo(px + TILE - o, py); ctx.lineTo(px + TILE - o, py + TILE); }
    ctx.stroke();
  }
}
let clouds = null;
function drawClouds(t) {
  if (G.auto) return;
  if (!clouds || clouds.W !== G.W) {
    clouds = { W: G.W, list: [] };
    for (let i = 0; i < Math.round(G.W / 9); i++) clouds.list.push({ x: Math.random() * G.W * TILE, y: Math.random() * G.H * TILE, r: 90 + Math.random() * 130, k: 0.6 + Math.random() * 0.6 });
  }
  const MW = G.W * TILE, v = viewRect();
  ctx.fillStyle = 'rgba(20,30,50,.09)';
  for (const c of clouds.list) {
    const x = ((c.x + t * 9) % (MW + 600)) - 300, y = c.y - t * 3 % 1;
    if (x + c.r * 1.6 < v[0] || x - c.r * 1.6 > v[2] || y + c.r < v[1] || y - c.r > v[3]) continue;
    ctx.beginPath();
    ctx.ellipse(x, y, c.r, c.r * 0.55 * c.k, 0, 0, 7);
    ctx.ellipse(x + c.r * 0.6, y + c.r * 0.12, c.r * 0.7, c.r * 0.4 * c.k, 0, 0, 7);
    ctx.ellipse(x - c.r * 0.55, y + c.r * 0.08, c.r * 0.6, c.r * 0.35 * c.k, 0, 0, 7);
    ctx.fill();
  }
}
function drawFire(b, t) {
  const x0 = b.tx * TILE, y0 = b.ty * TILE, w = b.w * TILE;
  const n = b.hp < b.maxhp * 0.3 ? 4 : 2;
  for (let i = 0; i < n; i++) {
    const fx = x0 + w * (0.25 + 0.5 * hash2(b.id, i, 3)), fy = y0 + 18 + hash2(b.id, i, 4) * 20;
    const f = Math.sin(t * 13 + i * 2) * 2, hgt = 12 + Math.sin(t * 9 + i) * 3;
    ctx.fillStyle = 'rgba(255,120,30,.85)'; ctx.beginPath(); ctx.moveTo(fx - 6, fy); ctx.quadraticCurveTo(fx - 4 + f, fy - hgt * 0.6, fx + f, fy - hgt); ctx.quadraticCurveTo(fx + 4 + f, fy - hgt * 0.5, fx + 6, fy); ctx.fill();
    ctx.fillStyle = 'rgba(255,220,90,.9)'; ctx.beginPath(); ctx.moveTo(fx - 3, fy); ctx.quadraticCurveTo(fx - 2 + f, fy - hgt * 0.4, fx + f * 0.6, fy - hgt * 0.65); ctx.quadraticCurveTo(fx + 2, fy - hgt * 0.3, fx + 3, fy); ctx.fill();
  }
}
let vigCache = null;
function drawVignette() {
  if (G.auto) return;
  if (!vigCache || vigCache.w !== VW || vigCache.h !== VH) {
    const g = ctx.createRadialGradient(VW / 2, VH / 2, Math.min(VW, VH) * 0.35, VW / 2, VH / 2, Math.max(VW, VH) * 0.75);
    g.addColorStop(0, 'rgba(255,200,120,0)'); g.addColorStop(0.6, 'rgba(60,30,0,.08)'); g.addColorStop(1, 'rgba(20,10,0,.42)');
    vigCache = { w: VW, h: VH, g };
  }
  ctx.fillStyle = 'rgba(255,190,110,.05)'; ctx.fillRect(0, 0, VW, VH);
  ctx.fillStyle = vigCache.g; ctx.fillRect(0, 0, VW, VH);
}

// ============================================================ мини-карта
const mini = $('mini'), mctx = mini.getContext('2d');
let miniBase = null;
function buildMiniBase() {
  miniBase = document.createElement('canvas'); miniBase.width = G.W; miniBase.height = G.H;
  const g = miniBase.getContext('2d'), img = g.createImageData(G.W, G.H);
  for (let i = 0; i < G.W * G.H; i++) {
    const t = G.terrain[i];
    const c = t === T_WATER ? [50, 96, 146] : t === T_FOREST ? [36, 76, 34] : t === T_SAND ? [196, 176, 120] : [98, 140, 64];
    img.data[i * 4] = c[0]; img.data[i * 4 + 1] = c[1]; img.data[i * 4 + 2] = c[2]; img.data[i * 4 + 3] = 255;
  }
  g.putImageData(img, 0, 0);
  G.miniDirty = false;
}
function drawMini() {
  if (G.miniDirty) buildMiniBase();
  const S = mini.width, s = S / G.W;
  mctx.imageSmoothingEnabled = false;
  mctx.drawImage(miniBase, 0, 0, S, S);
  for (const m of G.mines) if (!m.dead && (G.auto || G.explored[idx(m.tx, m.ty)])) { mctx.fillStyle = m.res === 'gold' ? '#ffd84a' : '#c8ccd0'; mctx.fillRect(m.tx * s, m.ty * s, 2 * s, 2 * s); }
  for (const b of G.blds) if (seen(b)) { mctx.fillStyle = b.team ? '#ff5a4a' : '#5a9aff'; mctx.fillRect(b.tx * s, b.ty * s, b.w * s, b.h * s); }
  for (const u of G.units) if (seen(u)) { mctx.fillStyle = u.team ? '#ff8a7a' : '#a8d0ff'; mctx.fillRect(u.x / TILE * s - 1, u.y / TILE * s - 1, 2, 2); }
  if (!G.auto && fogCv) { mctx.imageSmoothingEnabled = true; mctx.drawImage(fogCv, 0, 0, S, S); }
  const v = viewRect();
  mctx.strokeStyle = '#fff'; mctx.lineWidth = 1;
  mctx.strokeRect(v[0] / TILE * s, v[1] / TILE * s, (v[2] - v[0]) / TILE * s, (v[3] - v[1]) / TILE * s);
  if (G.ping) { mctx.strokeStyle = '#ff4030'; mctx.lineWidth = 2; mctx.beginPath(); mctx.arc(G.ping.x / TILE * s, G.ping.y / TILE * s, 6 + (G.ping.t % 1) * 6, 0, 7); mctx.stroke(); }
}
function miniTouch(e) {
  const r = mini.getBoundingClientRect();
  const x = (e.clientX - r.left) / r.width * G.W * TILE, y = (e.clientY - r.top) / r.height * G.H * TILE;
  centerCam(x, y);
}
mini.addEventListener('pointerdown', (e) => { if (!G) return; e.preventDefault(); mini.setPointerCapture(e.pointerId); miniTouch(e); mini._down = true; });
mini.addEventListener('pointermove', (e) => { if (G && mini._down) miniTouch(e); });
mini.addEventListener('pointerup', () => { mini._down = false; });

// ============================================================ ввод
const ptrs = new Map();
let gest = null;
function toWorld(sx, sy) { return [G.cam.x + sx / G.cam.z, G.cam.y + sy / G.cam.z]; }
cv.addEventListener('contextmenu', (e) => e.preventDefault());
cv.addEventListener('pointerdown', (e) => {
  if (!G || G.over) return;
  ensureAudio();
  cv.setPointerCapture(e.pointerId);
  ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (e.pointerType === 'mouse') {
    if (e.button === 2) { rightClick(e.clientX, e.clientY); return; }
    gest = { mode: e.button === 1 ? 'pan' : (G.placing ? 'pend' : 'pendm'), sx: e.clientX, sy: e.clientY, lx: e.clientX, ly: e.clientY, mouse: true };
    return;
  }
  if (ptrs.size === 2) {
    if (gest && gest.timer) clearTimeout(gest.timer);
    const [a, b] = [...ptrs.values()];
    gest = { mode: 'pinch', d0: Math.hypot(a.x - b.x, a.y - b.y), z0: G.cam.z, mx: (a.x + b.x) / 2, my: (a.y + b.y) / 2 };
    return;
  }
  if (ptrs.size > 2) return;
  gest = { mode: G.boxMode && !G.placing ? 'box' : 'pend', sx: e.clientX, sy: e.clientY, lx: e.clientX, ly: e.clientY };
  if (gest.mode === 'pend' && !G.placing) {
    const gg = gest;
    gg.timer = setTimeout(() => { if (gest === gg && gg.mode === 'pend') { gg.mode = 'box'; gg.moved = true; if (navigator.vibrate) navigator.vibrate(15); } }, 400);
  }
});
cv.addEventListener('pointermove', (e) => {
  if (!G) return;
  if (e.pointerType === 'mouse' && G.placing && !gest) { const [wx, wy] = toWorld(e.clientX, e.clientY); setGhost(wx, wy); }
  const p = ptrs.get(e.pointerId); if (!p) return;
  p.x = e.clientX; p.y = e.clientY;
  if (!gest) return;
  if (gest.mode === 'pinch') {
    if (ptrs.size < 2) return;
    const [a, b] = [...ptrs.values()];
    const d = Math.hypot(a.x - b.x, a.y - b.y), mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
    const [wx, wy] = toWorld(gest.mx, gest.my);
    G.cam.z = clamp(gest.z0 * d / gest.d0, 0.45, 2);
    G.cam.x = wx - mx / G.cam.z; G.cam.y = wy - my / G.cam.z;
    gest.mx = mx; gest.my = my; clampCam();
    return;
  }
  const dx = e.clientX - gest.lx, dy = e.clientY - gest.ly;
  const far = Math.hypot(e.clientX - gest.sx, e.clientY - gest.sy) > 9;
  if (gest.mode === 'pend' && far) { gest.mode = 'pan'; if (gest.timer) clearTimeout(gest.timer); }
  if (gest.mode === 'pendm' && far) gest.mode = 'box';
  if (gest.mode === 'pan') { G.cam.x -= dx / G.cam.z; G.cam.y -= dy / G.cam.z; clampCam(); }
  if (gest.mode === 'box' && far) gest.moved = true;
  if (G.placing && e.pointerType === 'mouse') { const [wx, wy] = toWorld(e.clientX, e.clientY); setGhost(wx, wy); }
  gest.lx = e.clientX; gest.ly = e.clientY;
});
function endPtr(e) {
  if (!G) return;
  ptrs.delete(e.pointerId);
  if (!gest) return;
  if (gest.timer) clearTimeout(gest.timer);
  if (gest.mode === 'pinch') { if (ptrs.size === 0) gest = null; else gest.mode = 'dead'; return; }
  if (gest.mode === 'dead') { if (ptrs.size === 0) gest = null; return; }
  if (gest.mode === 'pend' || gest.mode === 'pendm') { const [wx, wy] = toWorld(gest.sx, gest.sy); tapWorld(wx, wy, !!gest.mouse); }
  else if (gest.mode === 'box') {
    if (gest.moved) { const [a, b] = toWorld(gest.sx, gest.sy), [c, d] = toWorld(gest.lx, gest.ly); boxSelect(a, b, c, d); if (G.boxMode) { G.boxMode = false; uiDirty = true; } }
    else { const [wx, wy] = toWorld(gest.sx, gest.sy); tapWorld(wx, wy, !!gest.mouse); }
  }
  gest = null;
}
cv.addEventListener('pointerup', endPtr);
cv.addEventListener('pointercancel', (e) => { ptrs.delete(e.pointerId); gest = null; });
cv.addEventListener('wheel', (e) => {
  if (!G) return; e.preventDefault();
  const [wx, wy] = toWorld(e.clientX, e.clientY);
  G.cam.z = clamp(G.cam.z * (e.deltaY < 0 ? 1.12 : 1 / 1.12), 0.45, 2);
  G.cam.x = wx - e.clientX / G.cam.z; G.cam.y = wy - e.clientY / G.cam.z; clampCam();
}, { passive: false });
function rightClick(sx, sy) {
  if (G.placing) { G.placing = null; uiDirty = true; return; }
  const [wx, wy] = toWorld(sx, sy);
  if (ownSelUnits().length || (G.sel.length === 1 && G.sel[0].kind === 'b' && G.sel[0].team === 0)) {
    const saved = G.sel; tapWorldCommand(wx, wy); if (!G.sel.length) G.sel = saved;
  }
}
function tapWorldCommand(wx, wy) {
  // правый клик всегда приказ, а не выбор
  const hit = pickAt(wx, wy);
  if (hit && hit.kind === 'u' && hit.team === 0) { groupMove(ownSelUnits(), wx, wy, 'move'); marker(wx, wy, '#7cff6a'); return; }
  tapWorld(wx, wy, false);
}
const keys = new Set();
window.addEventListener('keydown', (e) => {
  keys.add(e.key.toLowerCase());
  if (!G) return;
  if (e.key === 'Escape') { if (G.placing) G.placing = null; else if (G.sel.length) G.sel = []; else togglePause(); uiDirty = true; }
  if (e.key === ' ') togglePause();
});
window.addEventListener('keyup', (e) => keys.delete(e.key.toLowerCase()));

// ============================================================ интерфейс
let uiDirty = true, uiSig = '';
const icons = {};
function makeIcons() {
  if (SPR.ok) {
    // вписать кадр в иконку по реальным границам непрозрачных пикселей (без тени)
    const fit = (im, sx, sy, sw, sh) => {
      const tc = document.createElement('canvas'); tc.width = sw; tc.height = sh;
      const tg = tc.getContext('2d'); tg.drawImage(im, sx, sy, sw, sh, 0, 0, sw, sh);
      const px = tg.getImageData(0, 0, sw, sh).data;
      let x0 = sw, y0 = sh, x1 = 0, y1 = 0;
      for (let y = 0; y < sh; y++) for (let x = 0; x < sw; x++) if (px[(y * sw + x) * 4 + 3] > 150) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
      if (x1 < x0) { x0 = 0; y0 = 0; x1 = sw - 1; y1 = sh - 1; }
      const c = document.createElement('canvas'); c.width = c.height = 72;
      const bw = x1 - x0 + 1, bh = y1 - y0 + 1, k = Math.min(66 / bw, 66 / bh);
      const g = c.getContext('2d'); g.imageSmoothingQuality = 'high';
      g.drawImage(tc, x0, y0, bw, bh, 36 - bw * k / 2, 36 - bh * k / 2, bw * k, bh * k);
      return c.toDataURL();
    };
    for (const t in UNIT) { const m = SPR.meta.units[t], sc = m.scale; icons[t] = fit(SPR.img['u_' + t], 0, 1 * m.ch * sc, m.cw * sc, m.ch * sc); }
    for (const t in BLD) { const m = SPR.meta.blds[t], sc = m.scale; icons[t] = fit(SPR.img['b_' + t], 0, 0, m.cw * sc, m.ch * sc); }
    return;
  }
  for (const t in UNIT) {
    const c = document.createElement('canvas'); c.width = c.height = 72;
    const g = c.getContext('2d'); g.translate(34, t === 'pike' ? 66 : 60); g.scale(t === 'pike' ? 1.6 : 2.1, t === 'pike' ? 1.6 : 2.1);
    drawUnitShape(g, t, 0, 1, 0, false, false, null, 0, 0);
    icons[t] = c.toDataURL();
  }
  for (const t in BLD) {
    const d = BLD[t], c = document.createElement('canvas'); c.width = c.height = 72;
    const g = c.getContext('2d'); const s = 64 / (Math.max(d.w, d.h) * TILE + 20);
    g.translate(4, 4 + 20 * s); g.scale(s, s);
    drawBuildingShape(g, t, 0, 0, 0, 1, 0);
    icons[t] = c.toDataURL();
  }
}
const resEl = $('res'), infoEl = $('info'), actsEl = $('acts');
function updateTop() {
  const R = G.res[0];
  resEl.innerHTML = RES.map((r) => `<span title="${RNAME[r]}">${RICON[r]} ${Math.floor(R[r])}</span>`).join('') +
    `<span class="${G.pop[0] >= G.cap[0] ? 'warn' : ''}">👥 ${G.pop[0]}/${G.cap[0]}</span>`;
}
function actBtn(a, icon, name, cost, extra) {
  const ok = !cost || canAfford(0, cost);
  return `<button class="act ${ok ? '' : 'no'}" data-a="${a}">${icon ? `<img src="${icons[icon]}">` : ''}<span class="n">${name}</span>${cost ? `<span class="c">${costStr(cost)}</span>` : ''}${extra || ''}</button>`;
}
function txtBtn(a, label) { return `<button class="act tx" data-a="${a}">${label}</button>`; }
function hpBar(e) { return `<div class="bar"><i style="width:${clamp(e.hp / e.maxhp, 0, 1) * 100}%"></i></div>`; }
function updatePanel() {
  const sel = G.sel;
  let sig = (G.placing ? 'P' + G.placing.type : '') + (G.amove ? 'A' : '') + sel.map((e) => e.id + (e.kind === 'b' ? (e.built ? 'b' : 'u') + e.queue.join('') : '')).join(',');
  sig += '|' + RES.map((r) => Math.floor(G.res[0][r] / 10)).join(',');
  // информация обновляется всегда
  let info = '';
  if (G.placing) info = `<div class="nm">${BLD[G.placing.type].name}</div><div>${BLD[G.placing.type].desc}</div><div>Нажмите на карту, чтобы выбрать место</div>`;
  else if (!sel.length) info = `<div class="nm">Ничего не выбрано</div><div>Тап по своему отряду или зданию. Удерживайте палец, чтобы выделить рамкой.</div>`;
  else if (sel.length === 1) {
    const e = sel[0];
    if (e.kind === 'u') {
      const d = UNIT[e.type];
      let st = '';
      if (e.task && e.task.type === 'build') st = 'Строит';
      else if (e.task) st = 'Добывает: ' + RNAME[e.task.res].toLowerCase() + (e.carry ? ` (${e.carry})` : '');
      else if (e.target) st = 'В бою';
      else if (e.goal) st = 'В пути';
      else st = 'Ожидает';
      info = `<div class="nm">${d.name}${e.team ? ' (враг)' : ''}</div>${hpBar(e)}<div>${Math.ceil(e.hp)}/${e.maxhp} · атака ${d.atk}</div><div>${e.team ? d.desc : st}</div>`;
    } else if (e.kind === 'b') {
      const d = BLD[e.type];
      info = `<div class="nm">${d.name}${e.team ? ' (враг)' : ''}</div>${hpBar(e)}<div>${Math.ceil(e.hp)}/${e.maxhp}</div>`;
      if (!e.built) info += `<div>Строится: ${Math.floor(e.progress / d.time * 100)}%</div>`;
      else if (e.queue.length) info += `<div class="bar pb"><i style="width:${e.qt / UNIT[e.queue[0]].time * 100}%"></i></div><div>${e.popBlocked ? 'Нужны дома!' : 'Обучение: ' + UNIT[e.queue[0]].name}</div>`;
      else info += `<div>${d.desc}</div>`;
    } else if (e.kind === 'm') {
      info = `<div class="nm">${e.res === 'gold' ? 'Золотая жила' : 'Каменоломня'}</div><div>Осталось: ${e.amount}</div><div>Отправьте сюда крестьян</div>`;
    }
  } else {
    const cnt = {}; for (const e of sel) cnt[e.type] = (cnt[e.type] || 0) + 1;
    info = `<div class="nm">Отряд: ${sel.length}</div><div class="grp">${Object.keys(cnt).map((t) => `<span>${UNIT[t] ? UNIT[t].name : t} ×${cnt[t]}</span>`).join('')}</div>`;
    if (G.amove) info += '<div>Натиск: тап по карте</div>';
  }
  infoEl.innerHTML = info;
  if (sig === uiSig && !uiDirty) {
    // обновить полоски очереди
    const qi = actsEl.querySelector('.qitem i'); if (qi && sel[0] && sel[0].queue && sel[0].queue.length) qi.style.transform = `scaleX(${sel[0].qt / UNIT[sel[0].queue[0]].time})`;
    return;
  }
  uiSig = sig; uiDirty = false;
  let h = '';
  const own = sel.filter((e) => e.team === 0);
  if (G.placing) h = txtBtn('cancel', '✕ Отмена');
  else if (own.length && own.every((e) => e.kind === 'u')) {
    const allPeas = own.every((e) => e.type === 'peasant');
    if (allPeas) { for (const t of BUILD_LIST) h += actBtn('b:' + t, t, BLD[t].name, BLD[t].cost); }
    else { h += txtBtn('form', '▦ Строй') + txtBtn('amove', G.amove ? '⚔ Натиск…' : '⚔ Натиск'); }
    h += txtBtn('stop', '■ Стоп') + txtBtn('desel', '✕ Снять');
  } else if (own.length === 1 && own[0].kind === 'b') {
    const b = own[0], d = BLD[b.type];
    if (b.built && d.trains) {
      for (const t of d.trains) { const q = b.queue.filter((x) => x === t).length; h += actBtn('t:' + t, t, UNIT[t].name, UNIT[t].cost, q ? `<span class="q">${q}</span>` : ''); }
      b.queue.forEach((t, k) => { h += `<button class="qitem" data-a="q:${k}"><img src="${icons[t]}">${k === 0 ? `<i style="transform:scaleX(${b.qt / UNIT[t].time})"></i>` : ''}</button>`; });
    }
    h += txtBtn('desel', '✕ Снять');
  } else if (sel.length) h = txtBtn('desel', '✕ Снять');
  actsEl.innerHTML = h;
}
actsEl.addEventListener('click', (e) => {
  const btn = e.target.closest('[data-a]'); if (!btn || !G) return;
  ensureAudio();
  const a = btn.dataset.a;
  if (a.startsWith('b:')) startPlacing(a.slice(2));
  else if (a.startsWith('t:')) {
    const b = G.sel[0], t = a.slice(2);
    if (!enqueue(b, t)) toast(b.queue.length >= 6 ? 'Очередь заполнена' : 'Не хватает: ' + missing(UNIT[t].cost)); else sfx('click');
  } else if (a.startsWith('q:')) {
    const b = G.sel[0], k = +a.slice(2), t = b.queue[k];
    if (t) { b.queue.splice(k, 1); pay(0, UNIT[t].cost, -1); if (k === 0) b.qt = 0; }
  } else if (a === 'cancel') G.placing = null;
  else if (a === 'stop') { for (const u of ownSelUnits()) { u.goal = null; u.target = null; u.task = null; u.cmd = null; } }
  else if (a === 'desel') { G.sel = []; G.amove = false; }
  else if (a === 'amove') G.amove = !G.amove;
  else if (a === 'form') {
    const us = ownSelUnits(); if (us.length) {
      let cx = 0, cy = 0; for (const u of us) { cx += u.x; cy += u.y; } cx /= us.length; cy /= us.length;
      const lead = us.find((u) => u.goal) || us[0];
      const fx = lead.face || 1, fd = lead.dir || 0;
      groupMove(us, cx, cy, 'move');
      // повернуть строй лицом по направлению взгляда
      for (const u of us) { u.face = fx; u.dir = fd; }
    }
  }
  uiDirty = true;
});
document.querySelectorAll('#quick button').forEach((b) => b.addEventListener('click', () => {
  if (!G) return; ensureAudio();
  const q = b.dataset.q;
  if (q === 'army') { G.sel = G.units.filter((u) => u.team === 0 && u.type !== 'peasant'); if (G.sel.length) { let cx = 0, cy = 0; for (const u of G.sel) { cx += u.x; cy += u.y; } centerCam(cx / G.sel.length, cy / G.sel.length); } else toast('Войск пока нет'); }
  else if (q === 'idle') {
    const idle = G.units.filter((u) => u.team === 0 && u.type === 'peasant' && !u.task && !u.goal && !u.target);
    if (idle.length) { G.sel = idle; centerCam(idle[0].x, idle[0].y); } else toast('Все крестьяне при деле');
  } else if (q === 'box') { G.boxMode = !G.boxMode; }
  uiDirty = true;
}));
$('pOk').addEventListener('click', () => { confirmPlace(); });
$('pNo').addEventListener('click', () => { if (G) { G.placing = null; uiDirty = true; } });
let toastT = 0;
function toast(msg) {
  if (!G || G.auto) return;
  const el = $('toast'); el.textContent = msg; el.classList.add('on');
  clearTimeout(toastT); toastT = setTimeout(() => el.classList.remove('on'), 2400);
}
function syncHudButtons() {
  $('bBox').classList.toggle('on', !!G.boxMode);
  $('place').classList.toggle('hidden', !G.placing);
  $('bSpeed').textContent = '×' + G.speed;
}

// ============================================================ меню
document.querySelectorAll('.seg').forEach((seg) => seg.addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b) return;
  seg.querySelectorAll('button').forEach((x) => x.classList.toggle('on', x === b));
  opts[seg.dataset.k] = seg.dataset.k === 'size' ? +b.dataset.v : b.dataset.v;
}));
$('bHelp').addEventListener('click', () => $('help').classList.toggle('hidden'));
$('bStart').addEventListener('click', () => {
  ensureAudio();
  newGame(opts, false);
  $('menu').classList.add('hidden'); $('hud').classList.remove('hidden');
  uiDirty = true;
  setTimeout(() => toast('Выделите крестьян и постройте ферму и дома'), 800);
});
function togglePause() {
  if (!G || G.over) return;
  G.paused = !G.paused;
  $('pausem').classList.toggle('hidden', !G.paused);
}
$('bPause').addEventListener('click', togglePause);
$('bResume').addEventListener('click', togglePause);
$('bSpeed').addEventListener('click', () => { if (!G) return; G.speed = G.speed >= 3 ? 1 : G.speed + 1; });
$('bSound').addEventListener('click', () => { soundOn = !soundOn; $('bSound').textContent = 'Звук: ' + (soundOn ? 'вкл' : 'выкл'); });
function toMenu() {
  G = null; $('pausem').classList.add('hidden'); $('endm').classList.add('hidden'); $('hud').classList.add('hidden'); $('menu').classList.remove('hidden');
}
$('bQuit').addEventListener('click', toMenu);
$('bMenu').addEventListener('click', toMenu);
document.addEventListener('visibilitychange', () => { if (document.hidden && G && !G.paused && !G.over) togglePause(); });
// кнопка «Назад» на Android
window.gameBack = function () {
  if (!G) return false;
  if (G.over) { toMenu(); return true; }
  if (G.placing) { G.placing = null; uiDirty = true; return true; }
  if (G.amove) { G.amove = false; uiDirty = true; return true; }
  if (G.sel.length && !G.paused) { G.sel = []; uiDirty = true; return true; }
  togglePause(); return true;
};

// ============================================================ звук
let AC = null, noiseBuf = null;
const lastS = {};
function ensureAudio() {
  try {
    if (!AC) {
      AC = new (window.AudioContext || window.webkitAudioContext)();
      noiseBuf = AC.createBuffer(1, AC.sampleRate, AC.sampleRate);
      const d = noiseBuf.getChannelData(0); for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    } else if (AC.state === 'suspended') AC.resume();
  } catch (e) { AC = null; }
}
function sfx(kind, x, y) {
  if (!AC || !soundOn || !G || G.auto) return;
  const now = AC.currentTime;
  if (lastS[kind] && now - lastS[kind] < (kind === 'shot' ? 0.06 : 0.1)) return;
  let vol = 1;
  if (x != null) {
    const v = viewRect(), cx = (v[0] + v[2]) / 2, cy = (v[1] + v[3]) / 2;
    const d = Math.hypot(x - cx, y - cy) / ((v[2] - v[0]) * 0.8);
    vol = clamp(1.2 - d, 0, 1); if (vol <= 0.02) return;
  }
  lastS[kind] = now;
  const out = AC.createGain(); out.connect(AC.destination);
  const noise = (dur, type, freq, gain) => {
    const s = AC.createBufferSource(); s.buffer = noiseBuf;
    const f = AC.createBiquadFilter(); f.type = type; f.frequency.value = freq;
    const g = AC.createGain(); g.gain.setValueAtTime(gain * vol, now); g.gain.exponentialRampToValueAtTime(0.001, now + dur);
    s.connect(f); f.connect(g); g.connect(out); s.start(now, Math.random() * 0.5); s.stop(now + dur);
  };
  const tone = (f0, f1, dur, gain, type) => {
    const o = AC.createOscillator(); o.type = type || 'sine'; o.frequency.setValueAtTime(f0, now); o.frequency.exponentialRampToValueAtTime(f1, now + dur);
    const g = AC.createGain(); g.gain.setValueAtTime(gain * vol, now); g.gain.exponentialRampToValueAtTime(0.001, now + dur);
    o.connect(g); g.connect(out); o.start(now); o.stop(now + dur);
  };
  switch (kind) {
    case 'shot': noise(0.18, 'bandpass', 1400, 0.35); noise(0.3, 'lowpass', 500, 0.25); break;
    case 'cannon': noise(0.8, 'lowpass', 260, 0.9); tone(90, 30, 0.6, 0.5); break;
    case 'impact': noise(0.4, 'lowpass', 400, 0.4); break;
    case 'melee': tone(1800 + Math.random() * 600, 900, 0.06, 0.08, 'square'); break;
    case 'click': tone(900, 700, 0.05, 0.08, 'triangle'); break;
    case 'order': tone(500, 650, 0.08, 0.1, 'triangle'); break;
    case 'done': tone(523, 523, 0.15, 0.12, 'triangle'); setTimeout(() => sfx2(659), 120); setTimeout(() => sfx2(784), 240); break;
  }
}
function sfx2(f) { if (!AC || !soundOn) return; const now = AC.currentTime; const o = AC.createOscillator(); o.type = 'triangle'; o.frequency.value = f; const g = AC.createGain(); g.gain.setValueAtTime(0.12, now); g.gain.exponentialRampToValueAtTime(0.001, now + 0.2); o.connect(g); g.connect(AC.destination); o.start(now); o.stop(now + 0.2); }

// ============================================================ главный цикл
let lastT = performance.now(), acc = 0;
function frame(now) {
  const dtReal = Math.min(0.1, (now - lastT) / 1000); lastT = now;
  if (G && !G.paused && !G.over) {
    // клавиатура
    const ks = 600 / G.cam.z * dtReal;
    if (keys.has('a') || keys.has('arrowleft')) G.cam.x -= ks;
    if (keys.has('d') || keys.has('arrowright')) G.cam.x += ks;
    if (keys.has('w') || keys.has('arrowup')) G.cam.y -= ks;
    if (keys.has('s') || keys.has('arrowdown')) G.cam.y += ks;
    clampCam();
    acc += dtReal * G.speed;
    let n = 0;
    while (acc >= STEP && n < 12) { update(STEP); acc -= STEP; n++; }
    if (n >= 12) acc = 0;
  } else if (G && G.over && !G.auto) {
    acc += dtReal; while (acc >= STEP) { G.time += 0; for (const f of G.fx) f.t += STEP; G.fx = G.fx.filter((f) => f.t < f.life); acc -= STEP; }
  }
  render(now);
  if (G && !G.auto) {
    G.uiT -= dtReal; if (G.uiT <= 0 || uiDirty) { G.uiT = 0.25; updateTop(); updatePanel(); syncHudButtons(); }
    G.miniT -= dtReal; if (G.miniT <= 0) { G.miniT = 0.3; drawMini(); }
  }
  requestAnimationFrame(frame);
}

// ============================================================ старт
resize();
makeIcons();
loadSprites();
const params = new URLSearchParams(location.search);
if (params.has('auto')) {
  // самопроверка: два ИИ играют друг с другом без отрисовки
  $('menu').classList.add('hidden');
  newGame({ diff: params.get('diff') || 'normal', size: +(params.get('size') || 64) }, true);
  window.__G = () => G;
  window.__sim = (sec) => { const n = Math.round(sec / STEP); for (let i = 0; i < n && !G.over; i++) update(STEP); return { time: G.time, over: G.over, winner: G.winner, units: [0, 1].map((t) => G.units.filter((u) => u.team === t).length), blds: [0, 1].map((t) => G.blds.filter((b) => b.team === t).length), res: G.res, stats: G.stats }; };
}
window.__start = (o) => { newGame(Object.assign({}, opts, o || {}), false); $('menu').classList.add('hidden'); $('hud').classList.remove('hidden'); uiDirty = true; };
window.__G = () => G; window.__gm = groupMove; window.__cf = computeField; window.__step = update;
requestAnimationFrame(frame);
})();
