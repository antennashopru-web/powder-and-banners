/* Спрайты из готовых CC0-моделей KayKit (Kay Lousberg, www.kaylousberg.com):
   Adventurers Character Pack 1.0 и Medieval Hexagon Pack 1.0.
   Персонажи переодеты под XVII век: свои пики, мушкеты, треуголки, кони и пушки. */
'use strict';
const KK = { chars: {}, med: {} };
const TEAM_HUE = [218, 356];

// ---------------------------------------------------------------- загрузка
async function kkLoad() {
  setup();
  for (const n of ['Knight', 'Rogue', 'Barbarian']) {
    KK.chars[n] = [];
    for (let team = 0; team < 2; team++) KK.chars[n][team] = await loadGLTF('kk/chars/' + n + '.glb');
  }
  const L = async (p) => (await loadGLTF('kk/med/' + p + '.gltf')).scene;
  const B = ['castle', 'home_A', 'market', 'barracks', 'archeryrange', 'blacksmith', 'tower_A', 'windmill'];
  for (const b of B) KK.med[b] = [await L('buildings/blue/building_' + b + '_blue'), await L('buildings/red/building_' + b + '_red')];
  for (const n of ['tree_single_A', 'tree_single_B', 'rock_single_A', 'rock_single_B', 'rock_single_C', 'rock_single_D', 'rock_single_E', 'resource_stone', 'barrel', 'crate_A_big', 'sack', 'resource_lumber', 'wheelbarrow'])
    KK.med[n] = await L('decoration/' + (/rock|tree/.test(n) ? 'nature/' : 'props/') + n);
  KK.med.scaffold = await L('buildings/neutral/building_scaffolding');
  for (const n in KK.chars) for (let t = 0; t < 2; t++) recolorChar(KK.chars[n][t], n, t);
}
function hueOf(r, g, b) {
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
  if (d === 0) return [0, 0, mx / 255];
  let h = mx === r ? ((g - b) / d) % 6 : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
  h *= 60; if (h < 0) h += 360;
  return [h, d / mx, mx / 255];
}
function hsv2rgb(h, s, v) {
  const c = v * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = v - c;
  const [r, g, b] = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
  return [(r + m) * 255, (g + m) * 255, (b + m) * 255];
}
// перекраска «формы» персонажа в цвет команды
function recolorChar(gl, name, team) {
  const band = { Knight: [[330, 360], [0, 20]], Rogue: [[120, 175]], Barbarian: [[195, 235]] }[name];
  const done = new Map();
  gl.scene.traverse((o) => {
    if (!o.isMesh || !o.material || !o.material.map) return;
    const src = o.material.map;
    if (!done.has(src)) {
      const im = src.image, c = document.createElement('canvas'); c.width = im.width; c.height = im.height;
      const g = c.getContext('2d'); g.drawImage(im, 0, 0);
      const d = g.getImageData(0, 0, c.width, c.height), p = d.data;
      for (let i = 0; i < p.length; i += 4) {
        const [h, s, v] = hueOf(p[i], p[i + 1], p[i + 2]);
        if (s < 0.3 || v < 0.12) continue;
        if (!band.some(([a, b]) => h >= a && h <= b)) continue;
        const [r, gg, b] = hsv2rgb(TEAM_HUE[team], Math.min(1, s * 1.05), team ? v * 0.92 : v * 0.95);
        p[i] = r; p[i + 1] = gg; p[i + 2] = b;
      }
      g.putImageData(d, 0, 0);
      const t = new THREE.CanvasTexture(c); t.flipY = src.flipY; t.colorSpace = THREE.SRGBColorSpace; t.magFilter = src.magFilter; t.minFilter = src.minFilter;
      done.set(src, t);
    }
    o.material = o.material.clone(); o.material.map = done.get(src);
  });
}
function shadows(obj) { obj.traverse((o) => { if (o.isMesh) { o.castShadow = true; o.receiveShadow = true; o.frustumCulled = false; } }); return obj; }

// ---------------------------------------------------------------- персонажи
function charRig(name, team, keep) {
  const gl = KK.chars[name][team];
  const root = SkeletonUtils.clone(gl.scene);
  const bones = {};
  root.traverse((o) => { if (o.isBone) bones[o.name] = o; });
  root.traverse((o) => { if (o.isMesh && !o.isSkinnedMesh && !keep.includes(o.name)) o.visible = false; });
  shadows(root);
  const mixer = new THREE.AnimationMixer(root);
  const clip = (n) => gl.animations.find((a) => a.name === n);
  return { root, bones, mixer, clip, gl };
}
function pose(rig, clipName, frac) {
  const c = rig.clip(clipName);
  rig.mixer.stopAllAction();
  const a = rig.mixer.clipAction(c); a.reset(); a.play();
  rig.mixer.setTime(c.duration * frac);
  rig.root.updateMatrixWorld(true);
}
// оружие, выровненное по направлению «родного» предмета в слоте
function attachAlong(rig, slotName, refName, weapon, back) {
  const slot = rig.bones[slotName]; let ref = null;
  slot.traverse((o) => { if (o.name === refName) ref = o; });
  const bb = new THREE.Box3().setFromBufferAttribute(ref.geometry.attributes.position);
  const sz = bb.getSize(new THREE.Vector3()), ctr = bb.getCenter(new THREE.Vector3());
  const ax = sz.x >= sz.y && sz.x >= sz.z ? 'x' : sz.y >= sz.z ? 'y' : 'z';
  const dir = new THREE.Vector3(); dir[ax] = Math.sign(ctr[ax]) || 1;
  dir.applyQuaternion(ref.quaternion);
  const holder = new THREE.Group(); holder.position.copy(ref.position);
  holder.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir);
  const ws = new THREE.Vector3(); slot.getWorldScale(ws);
  holder.scale.setScalar(1 / ws.x);
  weapon.position.y -= back || 0;
  holder.add(weapon); slot.add(holder);
  return holder;
}
function pikeMesh(len) {
  const g = new THREE.Group();
  g.add(cyl(0.035, 0.04, len, mat(0x8a6238), 0, len / 2, 0, 6));
  g.add(cone(0.07, 0.3, METAL(), 0, len + 0.13, 0, 6));
  return shadows(g);
}
function musketMesh() {
  const g = new THREE.Group();
  g.add(cyl(0.035, 0.035, 1.45, mat(0x3a3a3e, { metalness: 0.6, roughness: 0.4 }), 0, 0.55, 0, 6));
  g.add(box(0.08, 0.7, 0.11, mat(0x6a4024), 0, -0.35, 0));
  g.add(box(0.12, 0.28, 0.15, mat(0x5a3420), 0, -0.68, -0.03));
  return shadows(g);
}
function tricorne(team) {
  const g = new THREE.Group();
  const h = cyl(0.62, 0.62, 0.1, mat(0x1c1a1c), 0, 0, 0, 3); h.rotation.y = Math.PI; g.add(h);
  g.add(cyl(0.38, 0.42, 0.24, mat(0x1c1a1c), 0, 0.14, 0, 12));
  const tr = cyl(0.63, 0.63, 0.03, mat(0xe8d08a, { metalness: 0.4, roughness: 0.4 }), 0, 0.06, 0, 3); tr.rotation.y = Math.PI; g.add(tr);
  g.add(sph(0.08, mat(TEAMS[team].main), 0.42, 0.12, 0, 8, 6));
  return shadows(g);
}
const CH_K = 11;
function wrapChar(rig) {
  // модели KayKit смотрят вдоль +Z, наш «вперёд» — +X
  const g = new THREE.Group(); rig.root.rotation.y = Math.PI / 2; rig.root.scale.set(CH_K, CH_K, CH_K); g.add(rig.root); return g;
}
const DEATH = (rig) => pose(rig, 'Death_A_Pose', 0);

function kkPeasant(team, fr) {
  const rig = charRig('Barbarian', team, ['1H_Axe']);
  const hat = new THREE.Group();
  hat.add(cyl(0.6, 0.64, 0.06, mat(0xd8b45a, { map: TX.thatch }), 0, 0, 0, 14));
  hat.add(cyl(0.3, 0.37, 0.3, mat(0xd0a850, { map: TX.thatch }), 0, 0.2, 0, 12));
  hat.add(cyl(0.38, 0.38, 0.08, mat(TEAMS[team].main), 0, 0.08, 0, 12));
  hat.position.set(0, 0.66, 0); rig.bones.head.add(shadows(hat));
  if (fr.kind === 'walk') pose(rig, 'Walking_A', fr.i / 4);
  else if (fr.kind === 'act') pose(rig, '1H_Melee_Attack_Chop', [0.2, 0.42, 0.6][fr.i]);
  else if (fr.kind === 'dead') DEATH(rig);
  else pose(rig, 'Idle', 0);
  return wrapChar(rig);
}
function kkPike(team, fr) {
  const rig = charRig('Knight', team, ['Knight_Helmet', 'Knight_Cape']);
  attachAlong(rig, 'handslotr', '2H_Sword', pikeMesh(3.8), 0.7);
  if (fr.kind === 'walk') pose(rig, 'Walking_B', fr.i / 4);
  else if (fr.kind === 'act') pose(rig, '2H_Melee_Attack_Stab', [0.3, 0.45, 0.6][fr.i]);
  else if (fr.kind === 'dead') DEATH(rig);
  else pose(rig, '2H_Melee_Idle', 0);
  return wrapChar(rig);
}
function kkMusket(team, fr) {
  const rig = charRig('Rogue', team, ['Rogue_Cape']);
  attachAlong(rig, 'handslotr', '2H_Crossbow', musketMesh(), 0);
  const hat = tricorne(team); hat.position.set(0, 0.62, 0); rig.bones.head.add(hat);
  if (fr.kind === 'walk') pose(rig, 'Walking_B', fr.i / 4);
  else if (fr.kind === 'act') pose(rig, fr.i === 1 ? '2H_Ranged_Shoot' : '2H_Ranged_Aiming', fr.i === 1 ? 0.25 : 0.5);
  else if (fr.kind === 'dead') DEATH(rig);
  else pose(rig, '2H_Ranged_Aiming', 0.5);
  return wrapChar(rig);
}
function kkCav(team, fr) {
  const T = TEAMS[team];
  const { g, body } = horse(team ? 0x6a4a32 : 0x4f3524, T.main, (fr.i || 0) * Math.PI / 2, fr.kind === 'walk');
  const rig = charRig('Knight', team, ['Knight_Helmet', 'Knight_Cape', '1H_Sword']);
  // ноги из сидячей позы, руки из атаки
  pose(rig, 'Sit_Chair_Pose', 0);
  const legQ = {};
  for (const n in rig.bones) if (/leg|foot|toe|hips/i.test(n)) legQ[n] = rig.bones[n].quaternion.clone();
  const legP = rig.bones.hips ? rig.bones.hips.position.clone() : null;
  if (fr.kind === 'act') pose(rig, '1H_Melee_Attack_Slice_Diagonal', [0.25, 0.42, 0.6][fr.i]);
  else pose(rig, 'Idle', 0);
  for (const n in legQ) rig.bones[n].quaternion.copy(legQ[n]);
  if (legP) rig.bones.hips.position.copy(legP);
  rig.root.updateMatrixWorld(true);
  const w = wrapChar(rig); w.position.set(-1, 6.5, 0); w.scale.setScalar(0.68); body.add(w);
  return g;
}
function kkCannon(team, fr) {
  const g = buildCannon(team, fr);
  const rig = charRig('Barbarian', team, ['Barbarian_Hat']);
  if (fr.kind === 'act') pose(rig, 'Interact', [0.2, 0.5, 0.8][fr.i]);
  else if (fr.kind === 'walk') pose(rig, 'Walking_A', fr.i / 4);
  else pose(rig, 'Idle', 0);
  const w = wrapChar(rig); w.position.set(-20, 0, 7); w.scale.setScalar(0.95); g.add(w);
  return g;
}

// ---------------------------------------------------------------- здания и природа
function fitModel(src, targetW, targetD, yScale) {
  const obj = shadows(src.clone(true));
  const bb = new THREE.Box3().setFromObject(obj), sz = bb.getSize(new THREE.Vector3()), c = bb.getCenter(new THREE.Vector3());
  const k = Math.min(targetW / sz.x, targetD / sz.z);
  obj.scale.set(k, k * (yScale || 1) / KY, k);
  obj.position.set(-c.x * k, -bb.min.y * k * (yScale || 1) / KY, -c.z * k);
  const g = new THREE.Group(); g.add(obj); return g;
}
function place(g, src, size, x, z, ry, ys) { const o = fitModel(src, size, size, ys); o.position.x += x; o.position.z += z; o.rotation.y = ry || 0; g.add(o); return o; }
const KK_BLD = {
  hall: (t) => fitModel(KK.med.castle[t], 104, 100),
  house: (t) => fitModel(KK.med.home_A[t], 62, 62),
  store: (t) => fitModel(KK.med.market[t], 72, 66),
  barracks: (t) => fitModel(KK.med.barracks[t], 98, 96),
  stable: (t) => fitModel(KK.med.archeryrange[t], 100, 96),
  foundry: (t) => fitModel(KK.med.blacksmith[t], 98, 96),
  tower: (t) => fitModel(KK.med.tower_A[t], 60, 60),
  farm: (t) => {
    const g = buildFarm(t);
    // убрать собственные хижину и мельницу, поставить мельницу KayKit
    g.children.filter((o) => o.position.z <= -23 && o.position.y > 2).forEach((o) => g.remove(o));
    place(g, KK.med.windmill[t], 52, 24, -34);
    place(g, KK.med.sack, 8, -32, -28); place(g, KK.med.barrel, 7, -24, -30); place(g, KK.med.wheelbarrow, 12, -38, -38, 0.6);
    return g;
  },
};
function kkTree(k) {
  const pine = k < 4, src = pine ? KK.med.tree_single_A : KK.med.tree_single_B;
  const r = rng(500 + k), s = 0.85 + r() * 0.35;
  return fitModel(src, 27 * s, 27 * s, 1.0 + r() * 0.15);
}
function kkRocks(kind) {
  const g = new THREE.Group(), r = rng(kind === 'gold' ? 7 : 9);
  const rocks = ['rock_single_C', 'rock_single_D', 'rock_single_E', 'rock_single_B', 'rock_single_A'];
  const spots = [[-12, -10, 34], [12, -14, 30], [-14, 12, 26], [14, 10, 28], [0, 0, 36], [-2, -22, 22]];
  spots.forEach(([x, z, s], i) => { const o = place(g, KK.med[rocks[i % rocks.length]], s, x, z, r() * 6); });
  if (kind === 'gold') {
    const gm = mat(0xffd040, { metalness: 0.9, roughness: 0.25, emissive: 0x3a2800 });
    for (let i = 0; i < 16; i++) { const a = r() * 6.28, d = 6 + r() * 16; const n = mesh(new THREE.OctahedronGeometry(1.6 + r() * 1.8), gm, Math.cos(a) * d, 4 + r() * 10, Math.sin(a) * d); n.rotation.set(r() * 3, r() * 3, 0); g.add(n); }
  } else {
    place(g, KK.med.resource_stone, 14, 20, 18, 0.5);
  }
  return g;
}

// ---------------------------------------------------------------- рендер атласов с автоматической обрезкой
function cropBox(cv) {
  const g = cv.getContext('2d'), d = g.getImageData(0, 0, cv.width, cv.height).data;
  let x0 = cv.width, y0 = cv.height, x1 = -1, y1 = -1;
  for (let y = 0; y < cv.height; y++) for (let x = 0; x < cv.width; x++) if (d[(y * cv.width + x) * 4 + 3] > 6) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
  return x1 < 0 ? null : [x0, y0, x1, y1];
}
// рендерит набор кадров в большие ячейки и обрезает по общей рамке
function renderSet(makers, big, ax, ay) {
  const canv = [];
  let U = null;
  for (const mk of makers) {
    const [obj, rotY] = mk();
    const src = renderCell(obj, big, big, ax, ay, rotY);
    const c = document.createElement('canvas'); c.width = big; c.height = big; c.getContext('2d').drawImage(src, 0, 0);
    const b = cropBox(c); canv.push(c);
    if (b) U = U ? [Math.min(U[0], b[0]), Math.min(U[1], b[1]), Math.max(U[2], b[2]), Math.max(U[3], b[3])] : b;
  }
  U = U || [0, 0, 1, 1];
  // выравнивание до чётных чисел (2 пикселя спрайта = 1 пиксель игры)
  let x0 = U[0] - 2, y0 = U[1] - 2; x0 -= (x0 - ax) & 1; y0 -= (y0 - ay) & 1;
  let w = U[2] - x0 + 3, h = U[3] - y0 + 3; w += w & 1; h += h & 1;
  return { canv, x0, y0, w, h, ax: ax - x0, ay: ay - y0 };
}
function kkUnitAtlas(type, build) {
  const makers = [];
  for (let team = 0; team < 2; team++) for (let d = 0; d < 8; d++) FRAMES.forEach((fr) => makers.push(() => {
    if (fr.kind === 'dead' && type === 'cav') {
      const rotY = -d * Math.PI / 4, side = (d >= 2 && d <= 5) ? 1 : -1;
      const w = new THREE.Group(), inner = new THREE.Group(); inner.add(build(team, { kind: 'idle' })); w.add(inner);
      w.rotation.z = side * Math.PI / 2 * 0.95; w.position.set(side * 8, 4, 0); inner.rotation.y = side > 0 ? Math.PI / 2 : -Math.PI / 2;
      return [w, 0];
    }
    return [build(team, fr.kind === 'dead' ? { kind: 'idle' } : fr), -d * Math.PI / 4];
  }));
  const big = 360, R = renderSet(makers, big, big / 2, Math.round(big * 0.62)), cols = FRAMES.length;
  const atlas = document.createElement('canvas'); atlas.width = R.w * cols; atlas.height = R.h * 16;
  const ag = atlas.getContext('2d');
  const fr = [];
  R.canv.forEach((c, i) => {
    const f = i % cols, row = Math.floor(i / cols);
    const bx = cropBox(c) || [R.x0, R.y0, R.x0 + 1, R.y0 + 1];
    // своя рамка у каждого кадра: рисуем меньше прозрачных пикселей
    let fx = Math.max(0, bx[0] - 1 - R.x0), fy = Math.max(0, bx[1] - 1 - R.y0);
    fx -= fx & 1; fy -= fy & 1;
    let fw = Math.min(R.w - fx, bx[2] + 2 - R.x0 - fx), fh = Math.min(R.h - fy, bx[3] + 2 - R.y0 - fy);
    fw += fw & 1; fh += fh & 1; fw = Math.min(fw, R.w - fx); fh = Math.min(fh, R.h - fy);
    fr.push(fx, fy, fw, fh);
    if (type === 'cannon' && f === 8) { ag.save(); ag.globalAlpha = 0.85; ag.filter = 'brightness(0.55) sepia(0.4)'; }
    ag.drawImage(c, R.x0, R.y0, R.w, R.h, f * R.w, row * R.h, R.w, R.h);
    if (type === 'cannon' && f === 8) ag.restore();
  });
  return { url: atlas.toDataURL('image/png'), meta: { cw: R.w / S, ch: R.h / S, ax: R.ax / S, ay: R.ay / S, cols, scale: S, fr } };
}
function kkBuildingAtlas() {
  const out = {};
  for (const type in KK_BLD) {
    const big = 560, R = renderSet([0, 1].map((t) => () => [KK_BLD[type](t), 0]), big, Math.round(big * 0.42), Math.round(big * 0.7));
    const atlas = document.createElement('canvas'); atlas.width = R.w * 2; atlas.height = R.h;
    const ag = atlas.getContext('2d');
    R.canv.forEach((c, t) => ag.drawImage(c, R.x0, R.y0, R.w, R.h, t * R.w, 0, R.w, R.h));
    out[type] = { url: atlas.toDataURL('image/png'), meta: { cw: R.w / S, ch: R.h / S, ax: R.ax / S, ay: R.ay / S, scale: S } };
  }
  return out;
}
function kkNature() {
  const T = renderSet([0, 1, 2, 3, 4, 5, 6, 7].map((k) => () => [kkTree(k), k * 0.8]), 200, 100, 140);
  const ta = document.createElement('canvas'); ta.width = T.w * 8; ta.height = T.h;
  T.canv.forEach((c, k) => ta.getContext('2d').drawImage(c, T.x0, T.y0, T.w, T.h, k * T.w, 0, T.w, T.h));
  const trees = { url: ta.toDataURL('image/png'), meta: { cw: T.w / S, ch: T.h / S, ax: T.ax / S, ay: T.ay / S, n: 8, scale: S } };
  const Rk = renderSet(['gold', 'stone'].map((k) => () => [kkRocks(k), 0]), 300, 150, 190);
  const ra = document.createElement('canvas'); ra.width = Rk.w * 2; ra.height = Rk.h;
  Rk.canv.forEach((c, k) => ra.getContext('2d').drawImage(c, Rk.x0, Rk.y0, Rk.w, Rk.h, k * Rk.w, 0, Rk.w, Rk.h));
  const rocks = { url: ra.toDataURL('image/png'), meta: { cw: Rk.w / S, ch: Rk.h / S, ax: Rk.ax / S, ay: Rk.ay / S, scale: S } };
  return { trees, rocks };
}
window.kkRenderAll = async function (only) {
  await kkLoad();
  const res = { units: {}, blds: {} };
  const U = { peasant: kkPeasant, pike: kkPike, musket: kkMusket, cav: kkCav, cannon: kkCannon };
  for (const t in U) if (!only || only.includes(t)) res.units[t] = kkUnitAtlas(t, U[t]);
  if (!only || only.includes('blds')) res.blds = kkBuildingAtlas();
  if (!only || only.includes('nature')) Object.assign(res, kkNature());
  return res;
};
window.kkShot = async function (list) {
  await kkLoad(); const U = { peasant: kkPeasant, pike: kkPike, musket: kkMusket, cav: kkCav, cannon: kkCannon };
  return list.map(([t, team, d, f]) => renderCell(U[t](team, FRAMES[f]), 240, 240, 120, 170, -d * Math.PI / 4).toDataURL());
};
