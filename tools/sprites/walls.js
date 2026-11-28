/* Каменная стена и ворота: свои процедурные модели (three.js).
   Клетка 32×32 px. Стена = столб в центре клетки + полупролёты к соседним клеткам (wall_seg,
   от центра до края клетки вдоль +X; игра поворачивает их к соседям). Ворота стоят вдоль X
   и занимают клетку целиком, поэтому стыкуются с полупролётами соседей. */
'use strict';

function wallMats(team) {
  return {
    stone: mat(0x9a948a, { roughness: 0.95 }), dark: mat(0x6e6960, { roughness: 0.95 }),
    cap: mat(0xb4ada2, { roughness: 0.9 }), band: mat(TEAMS[team].main, { roughness: 0.7 }),
    wood: mat(0x6a4426, { roughness: 0.9 }), iron: mat(0x3a3a3e, { metalness: 0.6, roughness: 0.4 }),
    flag: mat(TEAMS[team].main, { side: THREE.DoubleSide }),
  };
}
function wallGroup() { const outer = new THREE.Group(); const g = new THREE.Group(); g.scale.y = 1 / KY; outer.add(g); return [outer, g]; }

// столб: квадратная башенка с зубцами и полосой цвета команды
function kkWall(team) {
  const M = wallMats(team), [outer, g] = wallGroup();
  g.add(box(15, 3, 15, M.dark, 0, 1.5, 0));
  g.add(box(13, 21, 13, M.stone, 0, 13.5, 0));
  g.add(box(13.6, 2, 13.6, M.band, 0, 19, 0));
  g.add(box(15, 2.5, 15, M.cap, 0, 25, 0));
  for (const [x, z] of [[-5.5, -5.5], [5.5, -5.5], [-5.5, 5.5], [5.5, 5.5]]) g.add(box(4, 3.5, 4, M.cap, x, 28, z));
  if (typeof shadows === 'function') shadows(outer);
  return outer;
}
// полупролёт от центра клетки (x = 0) до её края (x = 16)
function kkWallSeg(team) {
  const M = wallMats(team), [outer, g] = wallGroup();
  g.add(box(16, 3, 11, M.dark, 8, 1.5, 0));
  g.add(box(16, 17, 9, M.stone, 8, 11.5, 0));
  g.add(box(16, 1.5, 10, M.cap, 8, 20.5, 0));
  for (const x of [4, 12]) for (const z of [-3.6, 3.6]) g.add(box(3.4, 3, 1.8, M.cap, x, 22.5, z));
  // швы кладки
  for (const y of [7, 13]) g.add(box(16.02, 0.5, 9.1, M.dark, 8, y, 0));
  if (typeof shadows === 'function') shadows(outer);
  return outer;
}
// ворота вдоль X: две башенки, арка, дубовые створки и знамя
function kkGate(team) {
  const M = wallMats(team), [outer, g] = wallGroup();
  for (const s of [-1, 1]) {
    g.add(box(9, 3, 14, M.dark, s * 11.5, 1.5, 0));
    g.add(box(8, 27, 12, M.stone, s * 11.5, 16.5, 0));
    g.add(box(8.6, 2, 12.6, M.band, s * 11.5, 24, 0));
    g.add(box(9, 2, 13, M.cap, s * 11.5, 31, 0));
    for (const z of [-4, 4]) g.add(box(3, 3, 3, M.cap, s * 11.5, 33.5, z));
  }
  g.add(box(16, 6, 11, M.stone, 0, 24, 0));      // перемычка
  g.add(box(16, 1.5, 11.5, M.cap, 0, 27.7, 0));
  for (const x of [-4.5, 4.5]) g.add(box(3, 3, 11, M.cap, x, 30, 0));
  for (const s of [-1, 1]) {                       // створки
    g.add(box(7.6, 20, 2.2, M.wood, s * 3.9, 11, 0));
    for (const y of [5, 16]) g.add(box(7.8, 1, 2.6, M.iron, s * 3.9, y, 0));
  }
  g.add(box(1.4, 1.4, 3, M.iron, 0, 11, 0));       // засов
  g.add(cyl(0.5, 0.5, 14, M.wood, 0, 36, 0, 5));   // знамя
  const flag = new THREE.Mesh(new THREE.PlaneGeometry(9, 6, 2, 1), M.flag); flag.position.set(4.6, 40, 0); g.add(flag);
  if (typeof shadows === 'function') shadows(outer);
  return outer;
}

if (typeof KK_BLD !== 'undefined') { KK_BLD.wall = kkWall; KK_BLD.gate = kkGate; }

// 2D: кадры полупролётов к востоку, югу, западу, северу и ворота поперёк (с севера на юг)
function kkWallParts() {
  const makers = [];
  for (let t = 0; t < 2; t++) {
    for (let k = 0; k < 4; k++) makers.push(() => [kkWallSeg(t), -k * Math.PI / 2]);
    makers.push(() => [kkGate(t), -Math.PI / 2]);
  }
  const big = 240, R = renderSet(makers, big, 120, 160), cols = 5;
  const atlas = document.createElement('canvas'); atlas.width = R.w * cols; atlas.height = R.h * 2;
  const ag = atlas.getContext('2d');
  R.canv.forEach((c, i) => ag.drawImage(c, R.x0, R.y0, R.w, R.h, (i % cols) * R.w, Math.floor(i / cols) * R.h, R.w, R.h));
  return { url: atlas.toDataURL('image/png'), meta: { cw: R.w / S, ch: R.h / S, ax: R.ax / S, ay: R.ay / S, cols, scale: S } };
}
