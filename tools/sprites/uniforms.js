/* Обмундирование XVII века для всех войск: морионы, широкополые шляпы с перьями, шишаки,
   перевязи цвета команды, патронташи, и новый конь (плавные формы, грива, чепрак).
   Подключается после troops.js и переопределяет kkPike, kkMusket, kkCav, kkDragoon, kkPeasant,
   kkCannon, kkMortar, kkGrenadier, kkHero. Всё своё, процедурное (фигуры KayKit — CC0). */
'use strict';

const BRASS = () => mat(0xd8b050, { metalness: 0.75, roughness: 0.35 });
const STEEL = () => mat(0xb8bec8, { metalness: 0.8, roughness: 0.28 });
const FELT = (c) => mat(c, { roughness: 0.9 });

// перо: несколько вытянутых капель, изогнутых назад
function plume(color, len, curl) {
  const g = new THREE.Group(), m = mat(color, { roughness: 0.8 });
  for (let k = 0; k < 4; k++) {
    const t = k / 3, s = sph(0.13 * (1 - t * 0.45), m, -t * len * 0.8, t * len * 0.35 - t * t * curl, 0, 8, 6);
    s.scale.set(2.2, 0.75, 1.0); s.rotation.z = -0.3 - t * 0.6; g.add(s);
  }
  return g;
}

// морион: стальной гребнистый шлем пикинёра с загнутыми полями и пером цвета команды (лицо открыто)
function morion(team) {
  const g = new THREE.Group(), st = STEEL();
  const dome = sph(0.43, st, 0, 0.12, 0, 14, 10); dome.scale.set(0.95, 0.9, 1.1); g.add(dome);
  const brim = cyl(0.66, 0.66, 0.05, st, 0, -0.1, 0, 18); brim.scale.set(0.82, 1, 1.25); g.add(brim);
  const crest = cyl(0.42, 0.42, 0.05, st, 0, 0.42, 0, 16); crest.rotation.z = Math.PI / 2; crest.scale.set(1, 1, 1.15); g.add(crest);
  g.add(cyl(0.45, 0.45, 0.04, BRASS(), 0, -0.07, 0, 18)).scale.set(0.82, 1, 1.25);
  const p = plume(TEAMS[team].main, 0.7, 0.2); p.rotation.y = Math.PI / 2; p.position.set(0, 0.35, -0.35); g.add(p);
  return shadows(g);
}

// широкополая шляпа кавалера: поля с одной стороны заломлены, лента и перо цвета команды
function cavalier(team, featherCol) {
  const g = new THREE.Group(), f = FELT(0x2a2220);
  g.add(cyl(0.74, 0.74, 0.05, f, 0, 0, 0, 20));
  const up = cyl(0.5, 0.5, 0.05, f, 0, 0, 0, 14); up.scale.set(1, 1, 0.5); up.rotation.x = 1.15; up.position.set(0, 0.22, 0.42); g.add(up);
  g.add(cyl(0.38, 0.43, 0.3, f, 0, 0.17, 0, 14));
  g.add(cyl(0.425, 0.43, 0.1, mat(TEAMS[team].main), 0, 0.08, 0, 14));
  g.add(sph(0.07, BRASS(), 0.0, 0.14, 0.43, 6, 4));
  const p = plume(featherCol || TEAMS[team].light, 1.15, 0.35); p.scale.setScalar(1.25); p.rotation.y = -Math.PI / 2 + 0.3; p.position.set(0.1, 0.3, 0.35); g.add(p);
  return shadows(g);
}

// шишак рейтара: купол с назатыльником «рачий хвост», наносник и перо
function lobsterPot(team) {
  const g = new THREE.Group(), st = STEEL();
  const dome = sph(0.46, st, 0, 0.08, 0, 14, 10); dome.scale.set(1, 0.95, 1.05); g.add(dome);
  for (let k = 0; k < 3; k++) { const l = box(0.62 - k * 0.04, 0.06, 0.2, st, 0, -0.08 - k * 0.12, -0.38 - k * 0.07); l.rotation.x = 0.35 + k * 0.15; g.add(l); }
  g.add(box(0.07, 0.42, 0.05, st, 0, -0.12, 0.48));
  g.add(box(0.7, 0.05, 0.16, st, 0, -0.05, 0.42));
  g.add(cyl(0.035, 0.035, 0.24, BRASS(), 0, 0.6, 0, 6));
  const p = plume(TEAMS[team].main, 0.6, 0.15); p.rotation.y = Math.PI / 2; p.position.set(0, 0.62, -0.05); g.add(p);
  return shadows(g);
}

// вязаная шапка пушкаря цвета команды
function gunnerCap(team) {
  const g = new THREE.Group();
  const c = sph(0.44, FELT(TEAMS[team].main), 0, 0.1, 0, 12, 8); c.scale.set(1, 0.75, 1); g.add(c);
  g.add(cyl(0.45, 0.46, 0.14, FELT(0x8a6a40), 0, -0.02, 0, 14));
  const tip = sph(0.16, FELT(TEAMS[team].dark), 0, 0.3, -0.42, 8, 6); g.add(tip);
  return shadows(g);
}

// крепление в координатах персонажа (в покое): предмет следует за костью при анимации
function attachRest(rig, boneName, obj) {
  const bone = rig.bones[boneName]; if (!bone) return null;
  rig.root.updateMatrixWorld(true);
  const holder = new THREE.Group();
  holder.applyMatrix4(bone.matrixWorld.clone().invert().multiply(rig.root.matrixWorld));
  holder.add(obj); bone.add(holder);
  return obj;
}
const HEAD_TOP = 2.18;   // макушка фигур KayKit в покое (голова ~1.1 в высоту и ширину)
function onHead(rig, obj, y, s) { obj.position.y = y == null ? HEAD_TOP : y; if (s) obj.scale.setScalar(s); return attachRest(rig, 'head', obj); }
// перевязь через плечо: кольцо вокруг торса, наклонённое по диагонали
function sashRing(rig, color, flip, flasks) {
  const g = new THREE.Group();
  const t = mesh(new THREE.TorusGeometry(0.47, 0.065, 6, 26), mat(color, { roughness: 0.8 }));
  t.rotation.x = Math.PI / 2; t.scale.set(1.3, 1, 0.92); g.add(t);
  if (flasks) for (let k = 0; k < 5; k++) {
    const a = Math.PI / 2 + (k - 2) * 0.3;
    const f = cyl(0.055, 0.055, 0.17, mat(0x3a2410), Math.cos(a) * 0.47 * 1.3 * 0.98, -0.1, Math.sin(a) * 0.47 * 0.92 + 0.05, 6);
    g.add(f);
  }
  g.rotation.z = flip ? -0.8 : 0.8; g.position.set(0, 0.92, 0.01);
  return attachRest(rig, 'chest', shadows(g));
}
function beltRing(rig, color) {
  const t = mesh(new THREE.TorusGeometry(0.43, 0.08, 6, 24), mat(color, { roughness: 0.8 }));
  t.rotation.x = Math.PI / 2; t.scale.set(1.0, 0.9, 1); t.position.set(0, 0.62, 0);
  return attachRest(rig, 'spine', shadows(t));
}

// ---------------------------------------------------------------- конь
function horse(color, cloth, ph, gallop) {
  const g = new THREE.Group(), hm = mat(color, { roughness: 0.55 }), dark = mat(0x1a120c, { roughness: 0.7 });
  const light = mat(new THREE.Color(color).lerp(new THREE.Color(0xffffff), 0.12).getHex(), { roughness: 0.55 });
  const body = new THREE.Group(); g.add(body);
  // туловище: капсула + грудь + круп
  const b = mesh(new THREE.CapsuleGeometry(4.0, 9.5, 8, 14), hm, 0, 12.6, 0); b.rotation.z = Math.PI / 2; b.scale.set(1, 1, 0.8); body.add(b);
  const chest = sph(4.6, hm, 5.6, 12.8, 0, 14, 10); chest.scale.set(1, 1.05, 0.78); body.add(chest);
  const rump = sph(4.7, hm, -5.8, 13.4, 0, 14, 10); rump.scale.set(1, 1, 0.85); body.add(rump);
  // шея и голова
  const neck = mesh(new THREE.CylinderGeometry(1.9, 3.0, 9, 10), hm); neck.position.set(9.6, 18.6, 0); neck.rotation.z = -0.72; neck.scale.set(1, 1, 0.75); body.add(neck);
  const head = new THREE.Group(); head.position.set(13.3, 22.2, 0); head.rotation.z = -1.95; body.add(head);
  const skull = mesh(new THREE.CapsuleGeometry(1.55, 3.6, 6, 10), hm, 0, 1.6, 0); skull.scale.set(1, 1, 0.8); head.add(skull);
  const muzzle = sph(1.45, light, 0, 4.0, 0, 10, 8); muzzle.scale.set(0.9, 0.85, 0.82); head.add(muzzle);
  for (const z of [0.55, -0.55]) { head.add(sph(0.22, dark, 0.65, 4.6, z, 6, 4)); head.add(sph(0.3, dark, -0.9, 1.5, z * 1.9, 6, 4)); }
  for (const z of [0.7, -0.7]) { const e = cone(0.45, 1.6, hm, -1.6, -0.3, z, 6); e.rotation.z = 1.2; head.add(e); }
  // уздечка цвета команды
  const br = mesh(new THREE.TorusGeometry(1.5, 0.15, 5, 12), mat(cloth), 0, 3.0, 0); br.rotation.y = Math.PI / 2; br.scale.set(1, 1, 0.75); head.add(br);
  // грива: ряд прядей вдоль шеи
  for (let k = 0; k < 7; k++) {
    const t = k / 6, m = box(0.9, 2.2 + Math.sin(t * 3) * 0.6, 1.0, dark, 7.4 + t * 5.0, 21.4 + t * 2.6 - t * t * 0.8, 0);
    m.rotation.z = -0.9 + t * 0.2; body.add(m);
  }
  // хвост: изогнутые пряди
  const tail = new THREE.Group(); tail.position.set(-10, 15, 0); body.add(tail);
  for (let k = 0; k < 4; k++) { const s = stick([-k * 0.9, -k * 2.2, 0], [-(k + 1) * 0.9 - 0.4, -(k + 1) * 2.2, 0], 1.1 - k * 0.15, dark, 6); tail.add(s); }
  tail.rotation.z = gallop ? -0.35 + Math.sin(ph) * 0.15 : 0.08;
  // чепрак: длинная попона цвета команды с золотой каймой по бокам, седло
  const cl = mat(cloth, { map: TX.cloth }), trim = mat(0xe8c870, { metalness: 0.4, roughness: 0.45 });
  for (const z of [3.3, -3.3]) {
    const s = box(8.2, 5.0, 0.35, cl, -0.6, 13.6, z); s.rotation.x = z > 0 ? -0.22 : 0.22; body.add(s);
    const tr = box(8.4, 0.45, 0.4, trim, -0.6, 11.15, z * 1.07); tr.rotation.x = s.rotation.x; body.add(tr);
  }
  body.add(box(8.2, 0.5, 6.6, cl, -0.6, 16.5, 0));
  body.add(box(5.4, 1.2, 5.0, mat(0x4a2c16, { roughness: 0.6 }), -0.4, 17.3, 0));
  body.add(box(1.2, 1.4, 4.2, mat(0x3a2210), 2.0, 18.0, 0));
  body.add(box(1.0, 1.0, 4.2, mat(0x3a2210), -2.9, 17.9, 0));
  for (const z of [2.6, -2.6]) body.add(cyl(0.12, 0.12, 4.5, mat(0x2a1a0e), -0.3, 15.3, z, 4));
  // ноги: бедро + голень с коленом, копыта
  const legs = [[6.2, 2.2], [6.2, -2.2], [-6.4, 2.3], [-6.4, -2.3]];
  legs.forEach(([x, z], k) => {
    const front = x > 0;
    const phase = gallop ? ph + (front ? 0 : Math.PI * 0.8) + (z > 0 ? 0.45 : 0) : 0;
    const L = new THREE.Group(); L.position.set(x, 11.2, z);
    const up = mesh(new THREE.CylinderGeometry(1.5, 1.05, 5.4, 8), hm, 0, -2.6, 0); L.add(up);
    const knee = new THREE.Group(); knee.position.set(0, -5.2, 0); L.add(knee);
    knee.add(cyl(0.72, 0.62, 4.6, hm, 0, -2.3, 0, 7));
    knee.add(cyl(0.95, 1.1, 1.0, dark, 0, -4.8, 0, 8));
    knee.add(cyl(0.8, 0.95, 0.9, mat(0xe8dcc8), 0, -4.1, 0, 8));   // «чулки»
    L.rotation.z = gallop ? Math.sin(phase) * 0.55 : 0;
    knee.rotation.z = gallop ? Math.max(0, -Math.sin(phase + 0.6)) * (front ? -0.9 : 0.8) : 0;
    body.add(L);
  });
  if (gallop) { body.rotation.z = Math.sin(ph) * 0.05; body.position.y = Math.abs(Math.sin(ph)) * 1.1; }
  return { g, body };
}

// ---------------------------------------------------------------- войска
function kkPeasant(team, fr) {
  const rig = charRig('Barbarian', team, ['1H_Axe']);
  const hat = new THREE.Group();
  hat.add(cyl(0.6, 0.64, 0.06, mat(0xd8b45a, { map: TX.thatch }), 0, 0, 0, 14));
  hat.add(cyl(0.3, 0.37, 0.3, mat(0xd0a850, { map: TX.thatch }), 0, 0.2, 0, 12));
  hat.add(cyl(0.38, 0.38, 0.08, mat(TEAMS[team].main), 0, 0.08, 0, 12));
  onHead(rig, shadows(hat), HEAD_TOP - 0.12, 1.15);
  beltRing(rig, TEAMS[team].main);
  if (fr.kind === 'walk') pose(rig, 'Walking_A', fr.i / 4);
  else if (fr.kind === 'act') pose(rig, '1H_Melee_Attack_Chop', [0.2, 0.42, 0.6][fr.i]);
  else if (fr.kind === 'dead') DEATH(rig);
  else pose(rig, 'Idle', 0);
  return wrapChar(rig);
}

function kkPike(team, fr) {
  // пикинёр: открытое лицо под морионом, кираса рыцаря, плащ и перевязь цвета команды
  const rig = charRig('Knight', team, ['Knight_Cape']);
  attachAlong(rig, 'handslotr', '2H_Sword', pikeMesh(3.8), 0.7);
  onHead(rig, morion(team), HEAD_TOP - 0.3, 1.0);
  sashRing(rig, TEAMS[team].main, false, false);
  if (fr.kind === 'walk') pose(rig, 'Walking_B', fr.i / 4);
  else if (fr.kind === 'act') pose(rig, '2H_Melee_Attack_Stab', [0.3, 0.45, 0.6][fr.i]);
  else if (fr.kind === 'dead') DEATH(rig);
  else pose(rig, '2H_Melee_Idle', 0);
  return wrapChar(rig);
}

function kkMusket(team, fr) {
  // мушкетёр: шляпа с пером, патронташ «апостолы» через плечо
  const rig = charRig('Rogue', team, ['Rogue_Cape']);
  attachAlong(rig, 'handslotr', '2H_Crossbow', musketMesh(), 0);
  onHead(rig, cavalier(team), HEAD_TOP - 0.12, 0.92);
  sashRing(rig, 0x5a3a1e, true, true);
  if (fr.kind === 'walk') pose(rig, 'Walking_B', fr.i / 4);
  else if (fr.kind === 'act') pose(rig, fr.i === 1 ? '2H_Ranged_Shoot' : '2H_Ranged_Aiming', fr.i === 1 ? 0.25 : 0.5);
  else if (fr.kind === 'dead') DEATH(rig);
  else pose(rig, '2H_Ranged_Aiming', 0.5);
  return wrapChar(rig);
}

function rider(rig, legsFrom) {
  pose(rig, 'Sit_Chair_Pose', 0);
  const legQ = {};
  for (const n in rig.bones) if (/leg|foot|toe|hips/i.test(n)) legQ[n] = rig.bones[n].quaternion.clone();
  const legP = rig.bones.hips ? rig.bones.hips.position.clone() : null;
  legsFrom();
  for (const n in legQ) rig.bones[n].quaternion.copy(legQ[n]);
  if (legP) rig.bones.hips.position.copy(legP);
  rig.root.updateMatrixWorld(true);
  const w = wrapChar(rig); w.position.set(-0.6, 9.4, 0); w.scale.setScalar(0.66);
  return w;
}

function kkCav(team, fr) {
  // рейтар: шишак, кираса, перевязь, палаш; конь с чепраком цвета команды
  const T = TEAMS[team];
  const { g, body } = horse(team ? 0x6a4a32 : 0x4f3524, T.main, (fr.i || 0) * Math.PI / 2, fr.kind === 'walk');
  const rig = charRig('Knight', team, ['Knight_Cape', '1H_Sword']);
  onHead(rig, lobsterPot(team), HEAD_TOP - 0.3, 1.2);
  sashRing(rig, T.main, false, false);
  body.add(rider(rig, () => {
    if (fr.kind === 'act') pose(rig, '1H_Melee_Attack_Slice_Diagonal', [0.25, 0.42, 0.6][fr.i]);
    else pose(rig, 'Idle', 0);
  }));
  return g;
}

function kkDragoon(team, fr) {
  // драгун: шляпа с пером, мушкет, светлый конь
  const T = TEAMS[team];
  const { g, body } = horse(team ? 0x8a6a48 : 0x3a2a1c, T.main, (fr.i || 0) * Math.PI / 2, fr.kind === 'walk');
  const rig = charRig('Rogue', team, ['Rogue_Cape']);
  attachAlong(rig, 'handslotr', '2H_Crossbow', musketMesh(), 0);
  onHead(rig, cavalier(team), HEAD_TOP - 0.12, 0.92);
  sashRing(rig, 0x5a3a1e, true, true);
  body.add(rider(rig, () => {
    if (fr.kind === 'act') pose(rig, fr.i === 1 ? '2H_Ranged_Shoot' : '2H_Ranged_Aiming', fr.i === 1 ? 0.25 : 0.5);
    else pose(rig, '2H_Ranged_Aiming', 0.5);
  }));
  return g;
}

function gunner(team, fr) {
  const rig = charRig('Barbarian', team, []);
  onHead(rig, gunnerCap(team), HEAD_TOP - 0.3, 1.15);
  beltRing(rig, 0x5a3a1e);
  if (fr.kind === 'act') pose(rig, 'Interact', [0.2, 0.5, 0.8][fr.i]);
  else if (fr.kind === 'walk') pose(rig, 'Walking_A', fr.i / 4);
  else pose(rig, 'Idle', 0);
  return wrapChar(rig);
}
function kkCannon(team, fr) {
  const g = buildCannon(team, fr);
  const w = gunner(team, fr); w.position.set(-20, 0, 7); w.scale.setScalar(0.95); g.add(w);
  return g;
}
function kkMortar(team, fr) {
  const g = buildMortar(team, fr);
  const w = gunner(team, fr); w.position.set(-14, 0, -8); w.scale.setScalar(0.95); g.add(w);
  return g;
}

function kkGrenadier(team, fr) {
  const rig = charRig('Knight', team, ['Knight_Cape']);
  const cap = grenCap(team); cap.position.set(0, 0.5, 0); rig.bones.head.add(cap);
  const gr = grenade(); gr.position.set(0, 0.1, 0); rig.bones.handslotr.add(gr);
  sashRing(rig, 0x6a4426, true, false);
  const bag = box(0.34, 0.3, 0.16, mat(0x6a4426), 0, 0, 0); bag.position.set(-0.35, 0.75, 0.3); rig.root.add(shadows(bag));
  if (fr.kind === 'walk') pose(rig, 'Walking_A', fr.i / 4);
  else if (fr.kind === 'act') pose(rig, 'Throw', [0.25, 0.42, 0.6][fr.i]);
  else if (fr.kind === 'dead') DEATH(rig);
  else pose(rig, 'Idle', 0);
  return wrapChar(rig);
}

function kkHero(team, fr) {
  // генерал: шляпа с пышным белым и командным пером, золотая перевязь, плащ, шпага
  const rig = charRig('Knight', team, ['Knight_Cape', '1H_Sword']);
  const hat = cavalier(team, 0xf4f0e8);
  const p2 = plume(TEAMS[team].main, 0.7, 0.2); p2.rotation.y = -Math.PI / 2 - 0.4; p2.position.set(-0.15, 0.32, 0.3); hat.add(shadows(p2));
  onHead(rig, hat, HEAD_TOP - 0.1, 1.15);
  sashRing(rig, 0xe8c050, false, false);
  if (fr.kind === 'walk') pose(rig, 'Walking_A', fr.i / 4);
  else if (fr.kind === 'act') pose(rig, '1H_Melee_Attack_Slice_Diagonal', [0.25, 0.42, 0.6][fr.i]);
  else if (fr.kind === 'dead') DEATH(rig);
  else pose(rig, 'Idle', 0);
  const w = wrapChar(rig); w.scale.multiplyScalar(1.15);
  return w;
}
