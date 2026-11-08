/* Пререндер спрайтов «Порох и Знамёна» из процедурных 3D-моделей (three.js). */
'use strict';
function ad(p, c) { p.add(c); return c; }
const PITCH = 38 * Math.PI / 180;
const S = 2;                       // пикселей спрайта на пиксель игры
const KZ = 1 / Math.sin(PITCH);    // растяжение глубины, чтобы следы совпадали с клетками
const KY = 1.25;                   // растяжение высоты
const TEAMS = [
  { main: 0x2e5fc0, dark: 0x1a3570, light: 0x86aef0, trim: 0xf2d27a },
  { main: 0xc0342b, dark: 0x6e1a16, light: 0xf08a80, trim: 0xf2d27a },
];

// ---------------------------------------------------------------- генераторы текстур
function rng(seed) { let a = seed | 0; return () => { a = a + 0x6D2B79F5 | 0; let t = Math.imul(a ^ a >>> 15, 1 | a); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }
function canvasTex(w, h, draw, rep) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  const g = c.getContext('2d'); draw(g, w, h);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace;
  t.wrapS = t.wrapT = THREE.RepeatWrapping; if (rep) t.repeat.set(rep[0], rep[1]);
  t.anisotropy = 4; return t;
}
function hex(c) { return '#' + c.toString(16).padStart(6, '0'); }
function noiseFill(g, w, h, base, amp, seed, size) {
  const r = rng(seed); g.fillStyle = base; g.fillRect(0, 0, w, h);
  for (let i = 0; i < w * h / (size * size) * 1.5; i++) {
    const v = (r() - 0.5) * amp; g.fillStyle = v > 0 ? `rgba(255,255,255,${v})` : `rgba(0,0,0,${-v})`;
    g.fillRect(r() * w, r() * h, size * (0.5 + r()), size * (0.5 + r()));
  }
}
const TX = {};
function makeTextures() {
  TX.plaster = canvasTex(128, 128, (g, w, h) => noiseFill(g, w, h, '#e9e0c8', 0.12, 1, 3), [1, 1]);
  TX.stone = canvasTex(256, 256, (g, w, h) => {
    noiseFill(g, w, h, '#b9b2a2', 0.1, 2, 3); const r = rng(5);
    for (let y = 0; y < h; y += 22) { const off = (y / 22) % 2 ? 0 : 20; for (let x = -off; x < w; x += 40) {
      const sh = 0.85 + r() * 0.3; g.fillStyle = `rgba(${170 * sh | 0},${163 * sh | 0},${148 * sh | 0},.55)`; g.fillRect(x + 2, y + 2, 36, 18);
      g.strokeStyle = 'rgba(60,55,45,.55)'; g.lineWidth = 2; g.strokeRect(x + 1, y + 1, 38, 20); } }
  });
  TX.darkstone = canvasTex(256, 256, (g, w, h) => {
    noiseFill(g, w, h, '#8d8a84', 0.12, 3, 3); const r = rng(6);
    for (let y = 0; y < h; y += 26) for (let x = (y / 26) % 2 * 18 - 18; x < w; x += 36) {
      const sh = 0.8 + r() * 0.35; g.fillStyle = `rgba(${140 * sh | 0},${136 * sh | 0},${128 * sh | 0},.6)`; g.beginPath(); g.roundRect(x + 2, y + 2, 32, 22, 5); g.fill();
      g.strokeStyle = 'rgba(40,38,34,.6)'; g.lineWidth = 2; g.stroke(); }
  });
  TX.brick = canvasTex(256, 256, (g, w, h) => {
    noiseFill(g, w, h, '#6e3a2c', 0.1, 4, 2); const r = rng(7);
    for (let y = 0; y < h; y += 12) for (let x = (y / 12) % 2 * 12 - 12; x < w; x += 24) {
      const sh = 0.8 + r() * 0.4; g.fillStyle = `rgb(${150 * sh | 0},${72 * sh | 0},${52 * sh | 0})`; g.fillRect(x + 1, y + 1, 22, 10); }
  });
  TX.planks = canvasTex(128, 128, (g, w, h) => {
    const r = rng(8); for (let x = 0; x < w; x += 16) { const sh = 0.8 + r() * 0.35; g.fillStyle = `rgb(${142 * sh | 0},${98 * sh | 0},${58 * sh | 0})`; g.fillRect(x, 0, 16, h);
      g.fillStyle = 'rgba(40,20,5,.6)'; g.fillRect(x, 0, 1.5, h); for (let k = 0; k < 6; k++) { g.fillStyle = 'rgba(60,30,10,.25)'; g.fillRect(x + 3 + r() * 10, r() * h, 1, 10 + r() * 30); } }
  });
  const shingle = (base, seed) => canvasTex(128, 128, (g, w, h) => {
    const r = rng(seed); g.fillStyle = base; g.fillRect(0, 0, w, h);
    for (let y = 0; y < h; y += 10) for (let x = (y / 10) % 2 * 8 - 8; x < w; x += 16) {
      const sh = 0.75 + r() * 0.4; g.fillStyle = `rgba(${sh > 1 ? 255 : 0},${sh > 1 ? 255 : 0},${sh > 1 ? 255 : 0},${Math.abs(1 - sh) * 0.6})`; g.fillRect(x + 1, y, 14, 9);
      g.fillStyle = 'rgba(0,0,0,.35)'; g.fillRect(x, y + 8, 16, 2); g.fillRect(x, y, 1, 9); }
  });
  TX.tiles = shingle('#a8442a', 9);
  TX.woodroof = shingle('#5e4632', 10);
  TX.slate = shingle('#55585e', 11);
  TX.thatch = canvasTex(128, 128, (g, w, h) => { noiseFill(g, w, h, '#c9a65a', 0.0, 12, 2); const r = rng(13); for (let i = 0; i < 900; i++) { g.strokeStyle = `rgba(${r() > 0.5 ? '90,60,20' : '240,220,150'},.35)`; g.lineWidth = 1; const x = r() * w, y = r() * h; g.beginPath(); g.moveTo(x, y); g.lineTo(x + (r() - 0.5) * 3, y + 6 + r() * 8); g.stroke(); } });
  TX.teamRoof = TEAMS.map((t, k) => shingle(hex(t.dark), 20 + k));
  TX.cloth = canvasTex(64, 64, (g, w, h) => noiseFill(g, w, h, '#ffffff', 0.12, 14, 2));
  TX.leaf = canvasTex(128, 128, (g, w, h) => { noiseFill(g, w, h, '#ffffff', 0.0, 15, 2); const r = rng(16); for (let i = 0; i < 700; i++) { const v = r(); g.fillStyle = v > 0.5 ? `rgba(255,255,230,${(v - 0.5) * 0.5})` : `rgba(0,20,0,${(0.5 - v) * 0.7})`; g.beginPath(); g.ellipse(r() * w, r() * h, 3 + r() * 3, 2 + r() * 2, r() * 3, 0, 7); g.fill(); } });
  TX.bark = canvasTex(64, 64, (g, w, h) => { noiseFill(g, w, h, '#ffffff', 0, 17, 2); const r = rng(18); for (let i = 0; i < 60; i++) { g.fillStyle = 'rgba(0,0,0,.35)'; g.fillRect(r() * w, r() * h, 1.5, 8 + r() * 14); } });
  TX.rock = canvasTex(128, 128, (g, w, h) => noiseFill(g, w, h, '#ffffff', 0.3, 19, 4));
  TX.soil = canvasTex(128, 128, (g, w, h) => { noiseFill(g, w, h, '#7a5a34', 0.15, 20, 3); for (let y = 0; y < h; y += 8) { g.fillStyle = 'rgba(40,25,10,.35)'; g.fillRect(0, y, w, 2); } });
}

// ---------------------------------------------------------------- материалы и примитивы
const MC = new Map();
function mat(color, o = {}) {
  const key = color + JSON.stringify(Object.keys(o).map((k) => k + ':' + (o[k] && o[k].uuid ? o[k].uuid : o[k])));
  if (MC.has(key)) return MC.get(key);
  const m = new THREE.MeshStandardMaterial(Object.assign({ color, roughness: 0.85, metalness: 0 }, o));
  MC.set(key, m); return m;
}
const METAL = () => mat(0xc4c8cf, { metalness: 0.75, roughness: 0.32 });
function mesh(geo, m, x = 0, y = 0, z = 0) { const o = new THREE.Mesh(geo, m); o.position.set(x, y, z); o.castShadow = true; o.receiveShadow = true; return o; }
function box(w, h, d, m, x, y, z) { return mesh(new THREE.BoxGeometry(w, h, d), m, x, y, z); }
function cyl(rt, rb, h, m, x, y, z, seg = 10) { return mesh(new THREE.CylinderGeometry(rt, rb, h, seg), m, x, y, z); }
function sph(r, m, x, y, z, ws = 12, hs = 9) { return mesh(new THREE.SphereGeometry(r, ws, hs), m, x, y, z); }
function cone(r, h, m, x, y, z, seg = 10) { return mesh(new THREE.ConeGeometry(r, h, seg), m, x, y, z); }
function stick(a, b, r, m, seg = 6) { // цилиндр между точками a и b
  const va = new THREE.Vector3(...a), vb = new THREE.Vector3(...b), d = vb.clone().sub(va);
  const o = mesh(new THREE.CylinderGeometry(r, r, d.length(), seg), m);
  o.position.copy(va).add(vb).multiplyScalar(0.5);
  o.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), d.normalize());
  return o;
}
function gable(w, d, h, m, x, y, z) { // двускатная крыша: конёк вдоль X
  const sh = new THREE.Shape(); sh.moveTo(-d / 2, 0); sh.lineTo(d / 2, 0); sh.lineTo(0, h); sh.closePath();
  const geo = new THREE.ExtrudeGeometry(sh, { depth: w, bevelEnabled: false });
  geo.translate(0, 0, -w / 2); geo.rotateY(Math.PI / 2);
  // UV для черепицы: по длине ската
  const pos = geo.attributes.position, uv = geo.attributes.uv;
  for (let i = 0; i < pos.count; i++) uv.setXY(i, pos.getX(i) / 24, (pos.getY(i) + Math.abs(pos.getZ(i))) / 16);
  const o = mesh(geo, m, x, y, z); return o;
}
function hip(w, d, h, m, x, y, z) { const o = cone(1, h, m, 0, h / 2, 0, 4); o.rotation.y = Math.PI / 4; const g = new THREE.Group(); g.add(o); g.position.set(x, y, z); g.scale.set(w / Math.SQRT2, 1, d / Math.SQRT2); return g; }
function flagMesh(color, w, h) {
  const geo = new THREE.PlaneGeometry(w, h, 8, 2); const p = geo.attributes.position;
  for (let i = 0; i < p.count; i++) { const x = p.getX(i) + w / 2; p.setZ(i, Math.sin(x / w * 5) * 1.5 * (x / w)); }
  geo.computeVertexNormals(); geo.translate(w / 2, 0, 0);
  const o = mesh(geo, mat(color, { side: THREE.DoubleSide, map: TX.cloth })); return o;
}
function flagPole(color, x, y, z, hgt, s = 1) {
  const g = new THREE.Group(); g.position.set(x, y, z);
  g.add(cyl(0.5 * s, 0.6 * s, hgt, mat(0x5a3a1e), 0, hgt / 2, 0, 6));
  g.add(sph(0.9 * s, mat(0xd8b040, { metalness: 0.6, roughness: 0.35 }), 0, hgt + 0.6, 0, 8, 6));
  const f = flagMesh(color, 12 * s, 8 * s); f.position.set(0.4, hgt - 4 * s, 0); f.rotation.y = -0.5; g.add(f);
  return g;
}

// ---------------------------------------------------------------- люди
function human(o) {
  const g = new THREE.Group(), rig = { g };
  const coat = mat(o.coat, { map: TX.cloth }), pants = mat(o.pants), skin = mat(0xe8bf96, { roughness: 0.7 }), boot = mat(0x2a1d14, { roughness: 0.6 });
  rig.body = new THREE.Group(); g.add(rig.body);
  const legs = [];
  for (const side of [-1, 1]) {
    const L = new THREE.Group(); L.position.set(0, 9, side * 1.6);
    L.add(cyl(1.15, 1.0, 8, pants, 0, -4, 0, 8));
    L.add(box(3.4, 2.4, 2.4, boot, 0.5, -8.3, 0));
    rig.body.add(L); legs.push(L);
  }
  rig.legs = legs;
  const torso = new THREE.Group(); torso.position.y = 9; rig.body.add(torso); rig.torso = torso;
  const t = cyl(2.9, 3.3, 7.8, coat, 0, 4, 0, 10); t.scale.set(0.78, 1, 1); torso.add(t);
  const skirt = cyl(3.4, 4.1, 3.4, coat, 0, -0.6, 0, 10); skirt.scale.set(0.85, 1, 1); torso.add(skirt);
  torso.add(cyl(3.0, 3.0, 1.0, mat(o.belt || 0x3a2614), 0, 1.4, 0, 10));
  if (o.cuirass) { const c = cyl(3.15, 3.4, 5.5, METAL(), 0, 4.4, 0, 10); c.scale.set(0.82, 1, 1); torso.add(c); }
  if (o.collar) { const c = cyl(2.2, 2.6, 1, mat(o.collar), 0, 8.1, 0, 10); torso.add(c); }
  if (o.sash) { const s = stick([0, 7.6, -2.6], [0.4, 1.6, 2.8], 0.55, mat(o.sash)); s.scale.set(1, 1, 1); torso.add(s); }
  if (o.facing) torso.add(box(0.6, 6.5, 1.4, mat(o.facing), 2.35, 4.4, 0));
  const head = new THREE.Group(); head.position.y = 19.4; rig.body.add(head); rig.head = head;
  head.add(sph(2.35, skin, 0, 0, 0));
  ad(head, sph(2.4, mat(o.hair || 0x4a3020), -0.5, 0.3, 0, 10, 8)).scale.set(0.9, 0.95, 1);
  head.add(sph(0.35, mat(0x1a1a1a), 2.1, 0.3, 0.8, 6, 4)); head.add(sph(0.35, mat(0x1a1a1a), 2.1, 0.3, -0.8, 6, 4));
  head.add(sph(0.5, skin, 2.4, -0.3, 0, 6, 4));
  switch (o.hat) {
    case 'tricorne': { const h = cyl(3.9, 3.9, 1.6, mat(0x1c1a1c), 0, 2.3, 0, 3); h.rotation.y = Math.PI; head.add(h); head.add(cyl(2.3, 2.5, 1.6, mat(0x1c1a1c), 0, 3.2, 0, 10)); const tr = cyl(3.95, 3.95, 0.3, mat(o.trim || 0xe8d08a, { metalness: 0.4, roughness: 0.4 }), 0, 3.0, 0, 3); tr.rotation.y = Math.PI; head.add(tr); break; }
    case 'morion': { const m = METAL(); const c = sph(2.9, m, 0, 1.2, 0, 12, 6); c.scale.set(1.1, 0.85, 0.95); head.add(c); const b = cyl(4.4, 4.4, 0.4, m, 0, 0.9, 0, 14); b.scale.set(1.15, 1, 0.75); head.add(b); head.add(box(5.2, 1.6, 0.4, m, 0, 3.8, 0)); break; }
    case 'straw': head.add(cyl(4.4, 4.6, 0.5, mat(0xd8b45a, { map: TX.thatch }), 0, 1.6, 0, 14)); head.add(cyl(2.1, 2.5, 2.2, mat(0xd0a850, { map: TX.thatch }), 0, 2.9, 0, 12)); head.add(cyl(2.55, 2.55, 0.6, mat(o.band), 0, 2.2, 0, 12)); break;
    case 'helmet': { const m = METAL(); const c = sph(2.8, m, 0, 0.9, 0, 12, 6); head.add(c); head.add(box(0.5, 4, 0.5, m, 2.3, -0.8, 0)); const pl = sph(1.6, mat(o.plume), -1.4, 3.6, 0, 8, 6); pl.scale.set(1.6, 0.9, 0.7); head.add(pl); break; }
  }
  const arms = [];
  for (const side of [-1, 1]) {
    const A = new THREE.Group(); A.position.set(0, 16.4, side * 3.4);
    A.add(sph(1.35, coat, 0, 0, 0, 8, 6));
    A.add(cyl(1.15, 1.0, 6.8, coat, 0, -3.4, 0, 8));
    A.add(cyl(1.15, 1.15, 0.9, mat(o.cuff || 0xe8dcc0), 0, -6.4, 0, 8));
    A.add(sph(0.95, skin, 0, -7.3, 0, 8, 6));
    rig.body.add(A); arms.push(A);
  }
  rig.armL = arms[0]; rig.armR = arms[1];
  const hand = new THREE.Group(); hand.position.y = -7.3; rig.armR.add(hand); rig.hand = hand;
  const handL = new THREE.Group(); handL.position.y = -7.3; rig.armL.add(handL); rig.handL = handL;
  return rig;
}
function walkPose(rig, ph, amp = 0.55) {
  rig.legs[0].rotation.z = Math.sin(ph) * amp; rig.legs[1].rotation.z = -Math.sin(ph) * amp;
  rig.body.position.y = Math.abs(Math.cos(ph)) * 0.7;
}

// ---------------------------------------------------------------- юниты
function buildPeasant(team, frame) {
  const T = TEAMS[team];
  const rig = human({ coat: [0x56688e, 0x94503e][team], pants: 0x6a5238, hat: 'straw', band: T.main, belt: T.main, cuff: 0xd8c8a0, hair: 0x6a4a2a });
  const tool = new THREE.Group();
  tool.add(cyl(0.45, 0.45, 12, mat(0x7a5530), 0, 3, 0, 6));
  const head = box(1.2, 1.4, 4.2, METAL(), 0, 8.6, 0); head.position.x = 0; tool.add(head);
  tool.add(box(0.6, 2.6, 0.9, METAL(), 0, 8.6, 2.4));
  rig.hand.add(tool);
  const { kind, ph } = frame;
  if (kind === 'walk') { walkPose(rig, ph); rig.armL.rotation.z = Math.sin(ph) * 0.5; rig.armR.rotation.z = 0.3; tool.rotation.z = -1.7; }
  else if (kind === 'act') {
    const a = [2.7, 1.6, 0.6][frame.i]; rig.armR.rotation.z = a; rig.armL.rotation.z = a; rig.armL.rotation.x = 0.35; rig.armR.rotation.x = -0.25;
    tool.rotation.z = Math.PI - 0.35; rig.torso.rotation.z = -0.15 * frame.i;
  } else { rig.armR.rotation.z = 0.25; rig.armL.rotation.z = 0.1; tool.rotation.z = -0.25; }
  return rig.g;
}
function buildPike(team, frame) {
  const T = TEAMS[team];
  const rig = human({ coat: T.main, pants: 0x3a3a46, hat: 'morion', cuirass: true, cuff: T.light, facing: T.light, collar: 0xe8dcc0 });
  const pike = new THREE.Group();
  pike.add(cyl(0.38, 0.42, 40, mat(0x8a6238), 0, 14, 0, 6));
  pike.add(cone(0.8, 3.6, METAL(), 0, 35.6, 0, 6));
  rig.hand.add(pike);
  const { kind, ph } = frame;
  if (kind === 'act') {
    const th = [0, 2.5, 5][frame.i];
    rig.armR.rotation.z = 1.25; rig.armL.rotation.z = 1.45; rig.armL.rotation.x = 0.6; rig.armR.rotation.x = -0.15;
    pike.rotation.z = -1.57 + 1.25; pike.position.set(0, th * 0.2, 0);
    pike.rotation.z = -Math.PI / 2 - 1.25; // наконечник вперёд, горизонтально
    pike.position.y = 6 - th;
    rig.torso.rotation.z = -0.12; rig.legs[0].rotation.z = 0.45; rig.legs[1].rotation.z = -0.35;
  } else {
    if (kind === 'walk') walkPose(rig, ph);
    rig.armR.rotation.z = 0.35; rig.armL.rotation.z = kind === 'walk' ? Math.sin(ph) * 0.45 : 0.1;
    pike.rotation.z = -0.35; pike.position.y = -6;
  }
  return rig.g;
}
function buildMusket(team, frame) {
  const T = TEAMS[team];
  const rig = human({ coat: T.main, pants: 0xe8e0c8, hat: 'tricorne', cuff: T.light, facing: T.light, sash: 0xf2ead6, collar: 0xf2ead6 });
  const gun = new THREE.Group();
  gun.add(cyl(0.35, 0.35, 14, mat(0x3a3a3e, { metalness: 0.6, roughness: 0.4 }), 0, 5, 0, 6));
  gun.add(box(1.0, 6, 1.3, mat(0x6a4024), 0, -3, 0));
  gun.add(box(1.4, 2.6, 1.5, mat(0x5a3420), 0.4, -6.5, 0));
  rig.hand.add(gun);
  const { kind, ph } = frame;
  if (kind === 'act') {
    rig.armR.rotation.z = 1.45; rig.armL.rotation.z = 1.55; rig.armL.rotation.x = 0.75; rig.armR.rotation.x = 0.15;
    gun.rotation.z = -Math.PI / 2 - 1.45; gun.position.set(frame.i === 1 ? -1.2 : 0, -1, 0);
    rig.head.rotation.z = -0.1; rig.legs[0].rotation.z = 0.3; rig.legs[1].rotation.z = -0.3;
    if (frame.i === 1) rig.torso.rotation.z = 0.08;
  } else {
    if (kind === 'walk') walkPose(rig, ph);
    rig.armR.rotation.z = 0.5; rig.armR.rotation.x = 0.5; gun.rotation.z = -0.1; gun.rotation.x = -0.5; gun.position.y = 1.5;
    rig.armL.rotation.z = kind === 'walk' ? Math.sin(ph) * 0.45 : 0.1;
  }
  return rig.g;
}
function horse(color, cloth, ph, gallop) {
  const g = new THREE.Group(), hm = mat(color, { roughness: 0.6 }), dark = mat(0x1e140c, { roughness: 0.7 });
  const body = new THREE.Group(); g.add(body);
  const b = mesh(new THREE.CapsuleGeometry(4.3, 11, 6, 12), hm, 0, 12.5, 0); b.rotation.z = Math.PI / 2; b.scale.set(1, 1, 0.82); body.add(b);
  body.add(stick([6.5, 13.5, 0], [11, 21.5, 0], 2.4, hm, 10));
  const hd = mesh(new THREE.CapsuleGeometry(1.7, 5.2, 4, 10), hm, 13.8, 21.2, 0); hd.rotation.z = 2.1; body.add(hd);
  body.add(cone(0.6, 2, hm, 11.6, 25.2, 0.8, 5)); body.add(cone(0.6, 2, hm, 11.6, 25.2, -0.8, 5));
  body.add(stick([6.5, 18.5, 0], [10.8, 23.5, 0], 1.0, dark, 5));
  const tail = stick([-9.5, 14, 0], [-12.5, 6.5, 0], 1.1, dark, 6); body.add(tail);
  body.add(box(9, 1.2, 9.4, mat(cloth, { map: TX.cloth }), -0.5, 16.6, 0));
  body.add(box(6, 1.6, 6, mat(0x4a2e18), -0.5, 17.6, 0));
  const legs = [[6.5, 2.4], [6.5, -2.4], [-6.5, 2.4], [-6.5, -2.4]];
  legs.forEach(([x, z], k) => {
    const L = new THREE.Group(); L.position.set(x, 10, z);
    L.add(cyl(1.25, 0.8, 9, hm, 0, -4.5, 0, 7)); L.add(cyl(1.0, 1.1, 1.3, dark, 0, -9.3, 0, 7));
    const front = x > 0, phase = gallop ? ph + (front ? 0 : Math.PI * 0.8) + (z > 0 ? 0.4 : 0) : 0;
    L.rotation.z = gallop ? Math.sin(phase) * 0.6 : 0;
    body.add(L);
  });
  if (gallop) { body.rotation.z = Math.sin(ph) * 0.06; body.position.y = Math.abs(Math.sin(ph)) * 1.2; }
  return { g, body };
}
function buildCav(team, frame) {
  const T = TEAMS[team];
  const { g, body } = horse(team ? 0x6a4a32 : 0x4f3524, T.main, frame.ph || 0, frame.kind === 'walk');
  const rig = human({ coat: 0xd8c8a0, pants: 0x3a3028, hat: 'helmet', cuirass: true, plume: T.main, cuff: T.main, sash: T.main });
  rig.g.position.set(-0.5, 9.6, 0); body.add(rig.g);
  rig.legs[0].rotation.z = 1.25; rig.legs[1].rotation.z = 1.25; rig.legs[0].rotation.x = 0.45; rig.legs[1].rotation.x = -0.45;
  const sword = new THREE.Group(); sword.add(box(0.5, 13, 1.1, METAL(), 0, 6, 0)); sword.add(box(0.8, 0.6, 3, mat(0xd8b040, { metalness: 0.6 }), 0, -0.2, 0)); rig.hand.add(sword);
  rig.armL.rotation.z = 0.9;
  if (frame.kind === 'act') { const a = [2.8, 1.6, 0.5][frame.i]; rig.armR.rotation.z = a; sword.rotation.z = Math.PI - 0.4; rig.torso.rotation.z = -0.1 * frame.i; }
  else { rig.armR.rotation.z = 0.5; sword.rotation.z = -1.03; }
  return g;
}
function wheel(r, m) {
  const w = new THREE.Group();
  const t = mesh(new THREE.TorusGeometry(r, 0.7, 6, 18), m); w.add(t);
  for (let k = 0; k < 6; k++) { const s = box(0.6, r * 2, 0.6, m); s.rotation.z = k * Math.PI / 6; w.add(s); }
  ad(w, cyl(1.2, 1.2, 1.6, mat(0x3a2a1a), 0, 0, 0, 8)).rotation.x = Math.PI / 2;
  return w;
}
function buildCannon(team, frame) {
  const T = TEAMS[team];
  const g = new THREE.Group(), wood = mat(0x6e4a2a, { map: TX.planks }), iron = mat(0x2e2e30, { metalness: 0.6, roughness: 0.5 });
  const bronze = mat(0xb08a4a, { metalness: 0.85, roughness: 0.3 });
  const car = new THREE.Group(); g.add(car);
  const rec = frame.kind === 'act' && frame.i === 1 ? -2.5 : 0;
  car.add(box(16, 3.5, 1.4, wood, -2, 7, 3)); car.add(box(16, 3.5, 1.4, wood, -2, 7, -3));
  car.add(stick([-9, 7, 0], [-17, 1, 0], 1.3, wood));
  car.add(box(3, 1.5, 7.4, wood, 2, 6, 0));
  car.add(box(1.4, 1.4, 6.6, mat(T.main), -12.5, 3.5, 0));
  const barrel = new THREE.Group(); barrel.position.set(rec, 10, 0); barrel.rotation.z = frame.kind === 'act' ? 0.12 : 0.05; car.add(barrel);
  const bm = cyl(1.6, 2.4, 20, bronze, 3, 0, 0, 14); bm.rotation.z = -Math.PI / 2; barrel.add(bm);
  for (const x of [12.6, 6, -3]) { const r = mesh(new THREE.TorusGeometry(x > 10 ? 1.8 : 2.3, 0.45, 6, 14), bronze, x, 0, 0); r.rotation.y = Math.PI / 2; barrel.add(r); }
  barrel.add(sph(1.6, bronze, -7.4, 0, 0, 10, 8));
  const rot = frame.kind === 'walk' ? frame.ph : 0;
  for (const z of [-5, 5]) { const w = wheel(5.6, mat(0x5a3a1e)); w.position.set(0, 5.6, z); w.rotation.z = -rot; g.add(w); }
  return g;
}

// ---------------------------------------------------------------- здания
function buildHouse(team) {
  const g = new THREE.Group(), wood = mat(0x4a2e18);
  g.add(box(52, 22, 42, mat(0xffffff, { map: TX.plaster }), 0, 11, 0));
  const beams = (z, sgn) => {
    for (const x of [-25.5, -9, 9, 25.5]) g.add(box(1.6, 22, 1, wood, x, 11, z));
    g.add(box(52, 1.6, 1, wood, 0, 21.2, z)); g.add(box(52, 1.6, 1, wood, 0, 0.8, z)); g.add(box(52, 1.4, 1, wood, 0, 11, z));
    for (const [x0, x1] of [[-25, -9.5], [9.5, 25]]) { const s = stick([x0, 1.5, z], [x1, 10.5, z], 0.7, wood, 4); g.add(s); }
  };
  beams(21.1); beams(-21.1);
  g.add(box(1, 22, 42, wood, 26.1, 11, 0)); g.add(box(1, 22, 42, wood, -26.1, 11, 0));
  g.add(gable(58, 48, 18, mat(0xffffff, { map: TX.tiles }), 0, 22, 0));
  g.add(box(6, 14, 6, mat(0xffffff, { map: TX.brick }), 14, 34, -8)); g.add(box(7, 1.2, 7, mat(0x4a3a30), 14, 41, -8));
  g.add(box(9, 14, 1.2, mat(0x5a3418, { map: TX.planks }), 0, 7, 21.4));
  for (const x of [-17, 17]) { g.add(box(8, 8, 1.2, mat(0x3a4a5e, { roughness: 0.25, metalness: 0.3 }), x, 15, 21.5)); g.add(box(9.2, 1.2, 1.6, wood, x, 10.6, 21.6)); g.add(box(0.8, 8, 1.4, wood, x, 15, 21.7)); }
  g.add(box(10, 2, 3, mat(0x8a8274), 0, 1, 23.5));
  return g;
}
function buildHall(team) {
  const T = TEAMS[team], g = new THREE.Group(), stone = mat(0xffffff, { map: TX.stone }), roof = mat(0xffffff, { map: TX.teamRoof[team] });
  g.add(box(84, 30, 56, stone, 0, 15, -4));
  g.add(box(86, 2.4, 58, mat(0xd8d0bc), 0, 30, -4));
  g.add(hip(90, 62, 22, roof, 0, 31, -4));
  g.add(box(26, 36, 14, stone, 0, 18, 26));
  ad(g, gable(28, 16, 10, roof, 0, 36, 26)).rotation.y = Math.PI / 2;
  // башня с часами
  g.add(box(16, 30, 16, stone, 0, 52, 0));
  g.add(box(18, 2, 18, mat(0xd8d0bc), 0, 67, 0));
  g.add(hip(18, 18, 24, roof, 0, 68, 0));
  const clock = cyl(5, 5, 1, mat(0xf4ecd6), 0, 56, 8.3, 20); clock.rotation.x = Math.PI / 2; g.add(clock);
  g.add(box(0.7, 4, 0.6, mat(0x1a1a1a), 0, 57.6, 8.9)); g.add(box(3, 0.7, 0.6, mat(0x1a1a1a), 1.2, 56, 8.9));
  g.add(flagPole(T.main, 0, 90, 0, 14));
  // вход
  g.add(box(12, 16, 1.4, mat(0x4a2c16, { map: TX.planks }), 0, 8, 33.1));
  const arch = cyl(6, 6, 1.6, stone, 0, 16, 33.2, 16); arch.rotation.x = Math.PI / 2; g.add(arch);
  ad(g, cyl(5.2, 5.2, 1.7, mat(0x4a2c16), 0, 16, 33.3, 16)).rotation.x = Math.PI / 2;
  g.add(box(18, 2, 5, mat(0x9a9284), 0, 1, 36));
  for (const x of [-33, -21, 21, 33]) for (const y of [10, 22]) { g.add(box(6, 8, 1, mat(0x2e3a4e, { roughness: 0.25, metalness: 0.3 }), x, y, 24.2)); g.add(box(7.4, 1.2, 1.8, mat(0xd8d0bc), x, y - 4.6, 24.4)); }
  for (const x of [-36, 36]) g.add(flagPole(T.main, x, 31, 22, 0.01, 0.01));
  const ban = (x) => { const b = mesh(new THREE.PlaneGeometry(7, 14), mat(T.main, { map: TX.cloth, side: THREE.DoubleSide }), x, 20, 24.3); g.add(b); g.add(box(1.6, 3, 0.2, mat(T.trim), x, 20, 24.5)); };
  ban(-13); ban(13);
  return g;
}
function buildFarm(team) {
  const T = TEAMS[team], g = new THREE.Group(), r = rng(31 + team);
  g.add(box(92, 1.2, 62, mat(0xffffff, { map: TX.soil }), 0, 0.6, 14));
  const cropA = mat(0xd8b850), cropB = mat(0xa8c050);
  for (let row = 0; row < 8; row++) for (let k = 0; k < 15; k++) {
    const x = -42 + k * 6 + r() * 1.5, z = -12 + row * 7.4 + r();
    const h = 3 + r() * 2.5; const c = cone(1.4, h, row < 4 ? cropA : cropB, x, 1.2 + h / 2, z, 5); g.add(c);
  }
  const post = mat(0x6a4a2a);
  for (let k = 0; k <= 12; k++) { g.add(box(1, 5, 1, post, -46 + k * 7.66, 2.5, 46)); g.add(box(1, 5, 1, post, -46 + k * 7.66, 2.5, -17)); }
  for (const z of [46, -17]) { g.add(box(92, 0.8, 0.6, post, 0, 3.8, z)); g.add(box(92, 0.8, 0.6, post, 0, 1.8, z)); }
  // хижина
  g.add(box(28, 13, 16, mat(0xffffff, { map: TX.plaster }), -28, 6.5, -32));
  g.add(gable(32, 22, 11, mat(0xffffff, { map: TX.thatch }), -28, 13, -32));
  g.add(box(6, 9, 1, mat(0x5a3418), -28, 4.5, -23.8));
  // мельница
  const mw = new THREE.Group(); mw.position.set(26, 0, -32); g.add(mw);
  mw.add(cyl(6.5, 9, 30, mat(0xffffff, { map: TX.plaster }), 0, 15, 0, 12));
  mw.add(cone(9, 10, mat(0xffffff, { map: TX.thatch }), 0, 35, 0, 12));
  mw.add(box(5, 8, 1, mat(0x5a3418), 0, 4, 8.6));
  const hub = new THREE.Group(); hub.position.set(0, 28, 9.5); mw.add(hub);
  for (let k = 0; k < 4; k++) { const arm = new THREE.Group(); arm.rotation.z = k * Math.PI / 2 + 0.4; arm.add(box(1, 22, 0.8, mat(0x5a3a1e), 0, 11, 0)); arm.add(box(4.6, 15, 0.4, mat(0xeee4c8, { map: TX.cloth }), 2.8, 13, 0.4)); hub.add(arm); }
  g.add(flagPole(T.main, 44, 0, 44, 12, 0.7));
  return g;
}
function buildStore(team) {
  const T = TEAMS[team], g = new THREE.Group(), pl = mat(0xffffff, { map: TX.planks });
  g.add(box(50, 20, 40, pl, 0, 10, -2));
  g.add(gable(56, 46, 16, mat(0xffffff, { map: TX.woodroof }), 0, 20, -2));
  g.add(box(20, 16, 1.2, mat(0x4a2c16, { map: TX.planks }), 0, 8, 18.5));
  g.add(stick([-10, 0.5, 19.3], [10, 15.5, 19.3], 0.7, mat(0x3a2412), 4)); g.add(stick([10, 0.5, 19.3], [-10, 15.5, 19.3], 0.7, mat(0x3a2412), 4));
  const crate = mat(0xb08850, { map: TX.planks });
  g.add(box(7, 7, 7, crate, 19, 3.5, 24)); g.add(box(6, 6, 6, crate, 23, 3, 17.5)); ad(g, box(6, 6, 6, crate, 20, 10, 23)).rotation.y = 0.4;
  for (const [x, z] of [[-21, 24], [-15, 25.5]]) { g.add(cyl(3, 3, 8, mat(0x7a5230, { map: TX.planks }), x, 4, z, 12)); g.add(cyl(3.1, 3.1, 0.6, mat(0x3a3a3a), x, 6.5, z, 12)); g.add(cyl(3.1, 3.1, 0.6, mat(0x3a3a3a), x, 1.5, z, 12)); }
  g.add(flagPole(T.main, -24, 0, 18, 10, 0.6));
  return g;
}
function buildBarracks(team) {
  const T = TEAMS[team], g = new THREE.Group(), st = mat(0xffffff, { map: TX.darkstone });
  g.add(box(86, 24, 46, st, 0, 12, -10));
  g.add(gable(90, 52, 18, mat(0xffffff, { map: TX.teamRoof[team] }), 0, 24, -10));
  for (const x of [-30, 30]) g.add(box(4, 26, 4, mat(0x7a766e), x, 13, 13.5));
  g.add(box(14, 16, 1.2, mat(0x4a2c16, { map: TX.planks }), 0, 8, 13.3));
  for (const x of [-36, -22, 22, 36]) g.add(box(6, 7, 1, mat(0x2e3a4e, { roughness: 0.3 }), x, 14, 13.2));
  g.add(flagPole(T.main, 0, 0, 30, 30));
  // стойка с пиками
  for (let k = 0; k < 5; k++) g.add(stick([-38 + k * 2.4, 0, 22], [-36 + k * 2.4, 26, 18], 0.35, mat(0x8a6238), 5));
  g.add(box(14, 1, 1, mat(0x5a3a1e), -33, 8, 21));
  // мишень и бочки
  ad(g, cyl(4, 4, 1, mat(0xe8dcc0), 34, 8, 26, 16)).rotation.x = Math.PI / 2; ad(g, cyl(2, 2, 1.2, mat(T.main), 34, 8, 26.2, 16)).rotation.x = Math.PI / 2;
  g.add(box(1, 8, 1, mat(0x5a3a1e), 34, 4, 25.5));
  return g;
}
function buildStable(team) {
  const T = TEAMS[team], g = new THREE.Group(), pl = mat(0xffffff, { map: TX.planks });
  g.add(box(84, 20, 38, pl, 0, 10, -14));
  g.add(gable(88, 44, 15, mat(0xffffff, { map: TX.woodroof }), 0, 20, -14));
  for (const x of [-27, 0, 27]) { g.add(box(16, 15, 1.2, mat(0x1e140c), x, 7.5, 5.2)); g.add(box(18, 1.4, 1.6, mat(0x3a2412), x, 15.3, 5.4)); }
  const hay = mat(0xd8b850, { map: TX.thatch });
  g.add(box(10, 6, 7, hay, 32, 3, 22)); g.add(box(10, 6, 7, hay, 34, 3, 30)); ad(g, box(10, 6, 7, hay, 33, 9, 26)).rotation.y = 0.3;
  const post = mat(0x6a4a2a);
  for (let k = 0; k < 7; k++) g.add(box(1.2, 7, 1.2, post, -42 + k * 9, 3.5, 44));
  for (const y of [5.5, 2.8]) g.add(box(56, 0.9, 0.7, post, -15, y, 44));
  for (let k = 0; k < 4; k++) g.add(box(1.2, 7, 1.2, post, -42, 3.5, 44 - k * 9));
  for (const y of [5.5, 2.8]) g.add(box(0.7, 0.9, 27, post, -42, y, 30.5));
  g.add(cyl(4, 3.6, 4, mat(0x6a4a2a, { map: TX.planks }), -24, 2, 28, 12)); g.add(cyl(3.5, 3.5, 0.3, mat(0x4a6a8a, { roughness: 0.2 }), -24, 3.9, 28, 12));
  g.add(flagPole(T.main, 44, 0, 4, 22, 0.8));
  return g;
}
function buildFoundry(team) {
  const T = TEAMS[team], g = new THREE.Group(), br = mat(0xffffff, { map: TX.brick });
  g.add(box(70, 24, 46, br, -6, 12, -10));
  g.add(gable(74, 52, 16, mat(0xffffff, { map: TX.slate }), -6, 24, -10));
  g.add(box(14, 60, 14, br, 32, 30, -20)); g.add(box(16, 3, 16, mat(0x3a2a24), 32, 61, -20));
  const glow = mat(0xff8a2a, { emissive: 0xff6a10, emissiveIntensity: 1.6 });
  g.add(box(16, 12, 1, glow, -6, 6, 13.2)); g.add(box(20, 2, 2, mat(0x3a2a24), -6, 13, 13.5));
  const bronze = mat(0xb08a4a, { metalness: 0.85, roughness: 0.3 });
  for (const z of [24, 31]) { const b = cyl(1.4, 2.2, 18, bronze, 22, 2.2, z, 12); b.rotation.z = Math.PI / 2; g.add(b); }
  g.add(cone(9, 7, mat(0x222224, { roughness: 1 }), -32, 3.5, 26, 9));
  for (let k = 0; k < 4; k++) g.add(sph(1.6, mat(0x222224, { metalness: 0.4 }), 4 + k * 3.4, 1.6, 28, 8, 6));
  g.add(flagPole(T.main, -40, 0, 14, 24, 0.8));
  return g;
}
function buildTower(team) {
  const T = TEAMS[team], g = new THREE.Group(), st = mat(0xffffff, { map: TX.darkstone });
  g.add(cyl(20, 22, 50, st, 0, 25, 0, 16));
  g.add(cyl(23, 21, 4, st, 0, 52, 0, 16));
  for (let k = 0; k < 10; k++) { const a = k / 10 * Math.PI * 2; ad(g, box(5, 5, 4, st, Math.cos(a) * 21, 56.5, Math.sin(a) * 21)).rotation.y = -a; }
  for (const y of [18, 34]) g.add(box(2, 6, 1, mat(0x111111), 0, y, 21.6));
  g.add(box(8, 12, 1.4, mat(0x4a2c16, { map: TX.planks }), 0, 6, 21.7));
  const ban = mesh(new THREE.PlaneGeometry(8, 16), mat(T.main, { map: TX.cloth, side: THREE.DoubleSide }), 8.5, 40, 21.2); ban.rotation.y = 0.35; g.add(ban);
  g.add(flagPole(T.main, 0, 52, 0, 18, 0.9));
  return g;
}

// ---------------------------------------------------------------- природа
function buildPine(seed) {
  const r = rng(seed), g = new THREE.Group(), s = 0.85 + r() * 0.35;
  g.add(cyl(1.0 * s, 1.5 * s, 8 * s, mat(0x6a4426, { map: TX.bark }), 0, 4 * s, 0, 7));
  const greens = [0x1f4a22, 0x2a5a2a, 0x356a30];
  for (let k = 0; k < 4; k++) {
    const rr = (10 - k * 2.2) * s, h = (12 - k * 1.5) * s;
    const c = cone(rr, h, mat(greens[k % 3], { map: TX.leaf }), (r() - 0.5) * 0.8, (6 + k * 6.5) * s + h / 2, (r() - 0.5) * 0.8, 9);
    c.rotation.y = r() * 3; g.add(c);
  }
  return g;
}
function buildOak(seed) {
  const r = rng(seed), g = new THREE.Group(), s = 0.85 + r() * 0.3;
  g.add(cyl(1.3 * s, 2.0 * s, 12 * s, mat(0x5e3d20, { map: TX.bark }), 0, 6 * s, 0, 7));
  g.add(stick([0, 9 * s, 0], [4 * s, 15 * s, 2 * s], 0.9 * s, mat(0x5e3d20)));
  const greens = [0x2d5a26, 0x3f7a32, 0x4a8a3a, 0x356a2c];
  for (let k = 0; k < 7; k++) {
    const rr = (5 + r() * 3.5) * s;
    const b = mesh(new THREE.IcosahedronGeometry(rr, 1), mat(greens[k % 4], { map: TX.leaf, flatShading: false }), (r() - 0.5) * 11 * s, (16 + r() * 8) * s, (r() - 0.5) * 11 * s);
    b.scale.set(1, 0.85, 1); g.add(b);
  }
  return g;
}
function buildRocks(kind, seed) {
  const r = rng(seed), g = new THREE.Group();
  const base = kind === 'gold' ? [0x8a7a62, 0x9a8a70, 0x7a6a56] : [0x8c9096, 0x9ea2a8, 0x7a7e84];
  for (let k = 0; k < 13; k++) {
    const rr = 5 + r() * 8, a = r() * Math.PI * 2, d = r() * 20;
    const geo = new THREE.DodecahedronGeometry(rr, 0); const p = geo.attributes.position;
    for (let i = 0; i < p.count; i++) p.setXYZ(i, p.getX(i) * (0.8 + r() * 0.4), p.getY(i) * (0.6 + r() * 0.3), p.getZ(i) * (0.8 + r() * 0.4));
    geo.computeVertexNormals();
    const m = mesh(geo, mat(base[k % 3], { map: TX.rock, flatShading: true, roughness: 0.95 }), Math.cos(a) * d, rr * 0.45, Math.sin(a) * d * 0.9);
    m.rotation.set(r() * 3, r() * 3, r() * 3); g.add(m);
  }
  if (kind === 'gold') {
    const gm = mat(0xffd040, { metalness: 0.9, roughness: 0.25, emissive: 0x6a4a00, emissiveIntensity: 0.6 });
    for (let k = 0; k < 22; k++) { const a = r() * Math.PI * 2, d = r() * 22; const n = mesh(new THREE.OctahedronGeometry(1.2 + r() * 1.4, 0), gm, Math.cos(a) * d, 2 + r() * 9, Math.sin(a) * d * 0.9); n.rotation.set(r() * 3, r() * 3, 0); g.add(n); }
    g.add(box(8, 5, 5, mat(0x6a4a2a, { map: TX.planks }), 20, 2.5, 18)); for (const x of [16.5, 23.5]) ad(g, cyl(1.6, 1.6, 1, mat(0x2a2a2a), x, 1.6, 21, 10)).rotation.x = Math.PI / 2;
  } else {
    const cut = mat(0xb4b8bc, { map: TX.rock, roughness: 0.9 });
    g.add(box(8, 5, 6, cut, 20, 2.5, 18)); g.add(box(7, 4, 6, cut, 25, 2, 10)); ad(g, box(7, 4, 5, cut, 21, 7, 17)).rotation.y = 0.3;
  }
  return g;
}

// ---------------------------------------------------------------- сцена и рендер
let renderer, scene, camera, light, holder, ground;
function setup() {
  makeTextures();
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(1);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.05;
  renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.setClearColor(0x000000, 0);
  scene = new THREE.Scene();
  scene.add(new THREE.HemisphereLight(0xdfe8ff, 0x5a4a30, 1.1));
  light = new THREE.DirectionalLight(0xfff0d8, 2.6);
  light.position.set(-160, 260, -110); light.castShadow = true;
  light.shadow.mapSize.set(2048, 2048); light.shadow.radius = 3; light.shadow.bias = -0.0008; light.shadow.normalBias = 0.6;
  const sc = light.shadow.camera; sc.left = -110; sc.right = 110; sc.top = 110; sc.bottom = -110; sc.near = 1; sc.far = 800;
  scene.add(light); scene.add(light.target);
  const fill = new THREE.DirectionalLight(0xb8c8ff, 0.45); fill.position.set(150, 80, 200); scene.add(fill);
  ground = new THREE.Mesh(new THREE.PlaneGeometry(800, 800), new THREE.ShadowMaterial({ opacity: 0.38 }));
  ground.rotation.x = -Math.PI / 2; ground.receiveShadow = true; scene.add(ground);
  holder = new THREE.Group(); holder.scale.set(1, KY, KZ); scene.add(holder);
  camera = new THREE.OrthographicCamera(-1, 1, 1, -1, -2000, 2000);
  camera.position.set(0, Math.sin(PITCH) * 600, Math.cos(PITCH) * 600); camera.lookAt(0, 0, 0);
}
function renderCell(obj, cw, ch, ax, ay, rotY) {
  holder.clear();
  const pivot = new THREE.Group(); pivot.rotation.y = rotY || 0; pivot.add(obj); holder.add(pivot);
  camera.left = -ax / S; camera.right = (cw - ax) / S; camera.top = ay / S; camera.bottom = -(ch - ay) / S;
  camera.updateProjectionMatrix();
  renderer.setSize(cw, ch, false);
  renderer.render(scene, camera);
  return renderer.domElement;
}
const UNIT_SPEC = {
  peasant: { build: buildPeasant, cw: 72, ch: 92, ax: 34, ay: 74 },
  pike: { build: buildPike, cw: 96, ch: 128, ax: 46, ay: 104 },
  musket: { build: buildMusket, cw: 84, ch: 96, ax: 40, ay: 76 },
  cav: { build: buildCav, cw: 128, ch: 124, ax: 62, ay: 98 },
  cannon: { build: buildCannon, cw: 120, ch: 96, ax: 58, ay: 70 },
};
const FRAMES = [{ kind: 'idle' }, ...[0, 1, 2, 3].map((i) => ({ kind: 'walk', ph: i * Math.PI / 2, i })), ...[0, 1, 2].map((i) => ({ kind: 'act', i })), { kind: 'dead' }];
function renderUnitAtlas(type) {
  const sp = UNIT_SPEC[type], cols = FRAMES.length, rows = 16;
  const atlas = document.createElement('canvas'); atlas.width = sp.cw * cols; atlas.height = sp.ch * rows;
  const ag = atlas.getContext('2d');
  for (let team = 0; team < 2; team++) for (let d = 0; d < 8; d++) FRAMES.forEach((fr, f) => {
    let obj = sp.build(team, fr.kind === 'dead' ? { kind: 'idle' } : fr);
    let rotY = -d * Math.PI / 4;
    if (fr.kind === 'dead') {
      // падение всегда вбок по экрану, чтобы тело читалось в любом направлении
      const w = new THREE.Group(), inner = new THREE.Group(); inner.add(obj); inner.rotation.y = rotY; w.add(inner);
      const side = (d >= 2 && d <= 5) ? 1 : -1;
      if (type === 'cannon') { obj.rotation.z = 0.3; obj.position.y = -2.5; }
      else if (type === 'cav') { w.rotation.z = side * Math.PI / 2 * 0.95; w.position.set(side * 8, 4, 0); inner.rotation.y = side > 0 ? Math.PI / 2 : -Math.PI / 2; }
      else { w.rotation.z = side * Math.PI / 2; w.position.y = 2.2; }
      obj = w; rotY = 0;
    }
    const cvs = renderCell(obj, sp.cw, sp.ch, sp.ax, sp.ay, rotY);
    ag.drawImage(cvs, f * sp.cw, (team * 8 + d) * sp.ch);
  });
  return { url: atlas.toDataURL('image/png'), meta: { cw: sp.cw / S, ch: sp.ch / S, ax: sp.ax / S, ay: sp.ay / S, cols, scale: S } };
}
const BLD_SPEC = {
  hall: { build: buildHall, w: 3, h: 3, top: 104 }, house: { build: buildHouse, w: 2, h: 2, top: 58 }, farm: { build: buildFarm, w: 3, h: 3, top: 52 },
  store: { build: buildStore, w: 2, h: 2, top: 50 }, barracks: { build: buildBarracks, w: 3, h: 3, top: 62 }, stable: { build: buildStable, w: 3, h: 3, top: 52 },
  foundry: { build: buildFoundry, w: 3, h: 3, top: 80 }, tower: { build: buildTower, w: 2, h: 2, top: 84 },
};
function renderBuildingAtlas() {
  // каждое здание: 2 команды по горизонтали
  const out = {};
  for (const type in BLD_SPEC) {
    const sp = BLD_SPEC[type], fw = sp.w * 32, fh = sp.h * 32;
    const mL = 14, mR = 46, mB = 30, mT = sp.top;
    const cw = (fw + mL + mR) * S, ch = (fh + mT + mB) * S, ax = (mL + fw / 2) * S, ay = (mT + fh / 2) * S;
    const atlas = document.createElement('canvas'); atlas.width = cw * 2; atlas.height = ch;
    const ag = atlas.getContext('2d');
    for (let team = 0; team < 2; team++) { const cvs = renderCell(sp.build(team), cw, ch, ax, ay, 0); ag.drawImage(cvs, team * cw, 0); }
    out[type] = { url: atlas.toDataURL('image/png'), meta: { cw: cw / S, ch: ch / S, ax: ax / S, ay: ay / S, scale: S } };
  }
  return out;
}
function renderNature() {
  const cw = 64 * S, ch = 72 * S, ax = 26 * S, ay = 54 * S, n = 8;
  const atlas = document.createElement('canvas'); atlas.width = cw * n; atlas.height = ch;
  const ag = atlas.getContext('2d');
  for (let k = 0; k < n; k++) { const obj = k < 4 ? buildPine(100 + k) : buildOak(200 + k); ag.drawImage(renderCell(obj, cw, ch, ax, ay, k * 0.7), k * cw, 0); }
  const trees = { url: atlas.toDataURL('image/png'), meta: { cw: cw / S, ch: ch / S, ax: ax / S, ay: ay / S, n, scale: S } };
  const rw = 104 * S, rh = 96 * S, rax = 46 * S, ray = 58 * S;
  const ra = document.createElement('canvas'); ra.width = rw * 2; ra.height = rh;
  const rg = ra.getContext('2d');
  ['gold', 'stone'].forEach((k, i) => rg.drawImage(renderCell(buildRocks(k, 300 + i), rw, rh, rax, ray, 0), i * rw, 0));
  const rocks = { url: ra.toDataURL('image/png'), meta: { cw: rw / S, ch: rh / S, ax: rax / S, ay: ray / S, scale: S } };
  return { trees, rocks };
}
window.renderAll = function () {
  setup();
  const res = { units: {}, blds: renderBuildingAtlas() };
  for (const t in UNIT_SPEC) res.units[t] = renderUnitAtlas(t);
  Object.assign(res, renderNature());
  return res;
};
window.preview = function (kind, type, team, d, frame) {
  setup();
  if (kind === 'u') { const sp = UNIT_SPEC[type]; return renderCell(sp.build(team, FRAMES[frame]), sp.cw, sp.ch, sp.ax, sp.ay, -d * Math.PI / 4).toDataURL(); }
};
