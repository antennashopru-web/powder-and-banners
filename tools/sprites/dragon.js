/* Дракон и драконье логово: свои процедурные модели (three.js), без чужих ассетов.
   Размеры в пикселях игры, «вперёд» — +X, вверх — +Y. Летающий дракон висит над землёй
   (тело на высоте ~30), поэтому тень падает на землю сама. Кадры: idle, walk (взмахи крыльев,
   фаза ph), act (огненное дыхание, i = 0..2), dead (лежит на земле). */
'use strict';

const DRAGON_COL = [
  { body: 0x2f63b0, dark: 0x1c3a70, wing: 0x3d78c8, belly: 0xe8c878 },
  { body: 0xb0322a, dark: 0x6a1a16, wing: 0xc8463a, belly: 0xe8c070 },
];

function rod(a, b, r, m) {
  const A = new THREE.Vector3(...a), B = new THREE.Vector3(...b), d = B.clone().sub(A), L = d.length();
  const o = cyl(r, r * 0.8, L, m, 0, 0, 0, 5);
  o.position.copy(A).add(B).multiplyScalar(0.5);
  o.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), d.normalize());
  return o;
}

function wingGeo(side) {
  // перепонка: от плеча назад и наружу, с фестонами между «пальцами»
  const s = new THREE.Shape();
  const P = [[6, 0], [3, 12], [1, 24], [-2, 33], [-6, 26], [-9, 28], [-12, 18], [-16, 19], [-17, 9], [-20, 8], [-15, 0]];
  s.moveTo(P[0][0], P[0][1]);
  for (let i = 1; i < P.length; i++) s.lineTo(P[i][0], P[i][1]);
  s.lineTo(P[0][0], P[0][1]);
  const g = new THREE.ShapeGeometry(s);
  // плоскость XY -> XZ (размах по Z), левое крыло — зеркально
  g.rotateX(side > 0 ? Math.PI / 2 : -Math.PI / 2);
  return g;
}

function kkDragon(team, fr) {
  const C = DRAGON_COL[team];
  const mBody = mat(C.body, { roughness: 0.6 }), mDark = mat(C.dark, { roughness: 0.7 });
  const mBelly = mat(C.belly, { roughness: 0.7 }), mHorn = mat(0xeee4c8, { roughness: 0.5 });
  const mWing = mat(C.wing, { roughness: 0.8, side: THREE.DoubleSide });
  const mEye = mat(0xffd040, { emissive: 0xffa000, roughness: 0.3 });
  const mMouth = mat(0x5a1410, { roughness: 0.9 });

  const kind = fr.kind;
  const dead = kind === 'dead', act = kind === 'act';
  const ph = kind === 'walk' ? fr.ph : kind === 'idle' ? Math.PI * 0.5 : 0.9;
  const flap = dead ? -0.12 : act ? 0.35 : 0.15 + 0.75 * Math.sin(ph);
  const g = new THREE.Group();
  const body = new THREE.Group();
  body.position.set(0, dead ? 6 : 30 - 2.5 * Math.sin(ph), 0);
  if (dead) body.rotation.x = 0.35;
  g.add(body);

  // туловище и брюхо
  const torso = sph(1, mBody, 0, 0, 0, 14, 10); torso.scale.set(15, 8, 9); body.add(torso);
  const belly = sph(1, mBelly, 1, -3, 0, 12, 8); belly.scale.set(12, 5.5, 7); body.add(belly);
  // гребень на спине
  for (let i = 0; i < 6; i++) { const c = cone(1.6, 4.5 - i * 0.4, mDark, 9 - i * 4.2, 7.5 - Math.abs(i - 2) * 0.4, 0, 4); c.rotation.z = 0.35; body.add(c); }

  // шея: три звена к голове
  const lift = dead ? -0.9 : act ? [0.55, 0.05, -0.15][fr.i] : 0.75;
  const reach = act ? [0.8, 1.2, 1.3][fr.i] : 1;
  const neck = []; let nx = 11, ny = 3;
  for (let i = 0; i < 3; i++) {
    nx += 5.2 * reach * Math.cos(lift * (i + 1) / 3); ny += 5.2 * Math.sin(lift * (i + 1) / 3) + (dead ? -1.2 : 0);
    const s = sph(4.6 - i * 0.6, i % 2 ? mBody : mBody, nx, ny, 0, 10, 8); body.add(s); neck.push(s);
    const sp = cone(1.1, 3, mDark, nx - 1, ny + 4 - i * 0.4, 0, 4); sp.rotation.z = 0.4; body.add(sp);
  }
  // голова
  const head = new THREE.Group(); head.position.set(nx + 4, ny + 1, 0);
  head.rotation.z = dead ? -0.2 : act ? [0.35, -0.15, -0.3][fr.i] : -0.1;
  body.add(head);
  const skull = sph(1, mBody, 0, 0, 0, 12, 8); skull.scale.set(5.5, 4, 4.2); head.add(skull);
  head.add(box(8, 3.2, 4.6, mBody, 6, -0.6, 0));                 // морда
  head.add(box(2.5, 1.2, 3.6, mDark, 9.6, 0.6, 0));              // ноздри
  const jaw = new THREE.Group(); jaw.position.set(2, -2, 0);
  jaw.rotation.z = dead ? -0.25 : act ? [-0.25, -0.6, -0.75][fr.i] : -0.05;
  jaw.add(box(9, 1.6, 4.2, mBelly, 4, -0.6, 0));
  jaw.add(box(7.5, 0.6, 3.6, mMouth, 4, 0.3, 0));
  head.add(jaw);
  for (const z of [-2.2, 2.2]) {
    head.add(sph(0.9, mEye, 3.2, 1.6, z * 1.05, 6, 4));
    const h1 = cone(1.1, 7, mHorn, -3.5, 3.5, z, 6); h1.rotation.z = 1.9; head.add(h1);
    const h2 = cone(0.7, 4, mHorn, -1, 3.8, z * 1.1, 5); h2.rotation.z = 1.6; head.add(h2);
  }

  // хвост: звенья назад с волной
  let tx = -13, ty = 0;
  for (let i = 0; i < 6; i++) {
    const a = dead ? -0.05 : 0.18 * Math.sin(ph + i * 0.9) - 0.12;
    tx -= 5.2; ty += a * 5;
    const r = 4.2 - i * 0.6;
    const s = sph(r, mBody, tx, ty, (dead ? 2.2 : 1.6) * Math.sin(ph * 0.5 + i * 0.7) * (i / 5), 9, 7); body.add(s);
    if (i % 2 === 0) { const sp = cone(1, 2.6, mDark, tx, ty + r, 0, 4); body.add(sp); }
  }
  const tip = cone(3.2, 6, mDark, tx - 4.5, ty, 0, 4); tip.rotation.z = Math.PI / 2; tip.scale.set(1, 1, 0.35); body.add(tip);

  // лапы, поджатые в полёте
  for (const [x, z] of [[7, -5.5], [7, 5.5], [-7, -6], [-7, 6]]) {
    const leg = new THREE.Group(); leg.position.set(x, -4.5, z);
    leg.rotation.z = dead ? -1.2 : 0.9;
    leg.add(cyl(1.6, 1.3, 7, mBody, 0, -3.5, 0, 6));
    leg.add(box(3.2, 1.2, 2.6, mDark, 1.2, -7.2, 0));
    body.add(leg);
  }

  // крылья
  for (const side of [1, -1]) {
    const w = new THREE.Group(); w.position.set(3, 5, side * 6.5);
    w.rotation.x = -side * flap;
    if (dead) w.rotation.y = side * 0.15;
    const arm = cyl(1.1, 0.8, 33, mDark, 1, 0, side * 16.5, 6); arm.rotation.x = Math.PI / 2; w.add(arm);
    for (const [px, pz] of [[-6, 26], [-12, 18], [-17, 9]]) w.add(rod([0, 0.4, side * 31], [px, 0.4, side * pz], 0.5, mDark));
    w.add(mesh(wingGeo(side), mWing));
    const claw = cone(0.9, 3, mHorn, 1.5, 1.2, side * 33, 4); w.add(claw);
    body.add(w);
  }
  g.scale.setScalar(1.35);
  if (typeof shadows === 'function') shadows(g);
  return g;
}

// ---------------------------------------------------------------- логово (3×3 клетки = 96 px)
function kkLair(team) {
  const T = TEAMS[team];
  const outer = new THREE.Group();
  const g = new THREE.Group(); g.scale.y = 1 / KY; outer.add(g);
  const mDirt = mat(0x6e5a40, { roughness: 1 }), mRock = mat(0x7d7a74, { roughness: 0.95 }), mRockD = mat(0x5a5752, { roughness: 0.95 });
  const mCave = mat(0x120c08, { roughness: 1 }), mBone = mat(0xece4cc, { roughness: 0.6 });
  const mGold = mat(0xffd040, { metalness: 0.9, roughness: 0.25, emissive: 0x3a2800 });
  const mWood = mat(0x6a4426), mFlag = mat(T.main, { side: THREE.DoubleSide });
  const r = rng(team ? 77 : 33);
  // земляная площадка
  const base = cyl(46, 47, 3, mDirt, 0, 1.5, 0, 20); base.scale.z = 0.92; g.add(base);
  // скальный холм: кольцо больших камней, выше сзади
  const hill = sph(1, mRock, 0, 4, -10, 14, 10); hill.scale.set(34, 30, 26); g.add(hill);
  const hill2 = sph(1, mRockD, -16, 2, -2, 10, 8); hill2.scale.set(18, 20, 18); g.add(hill2);
  const hill3 = sph(1, mRockD, 18, 2, -4, 10, 8); hill3.scale.set(16, 22, 18); g.add(hill3);
  for (let i = 0; i < 9; i++) {
    const a = Math.PI * (1.15 + i * 0.09 * 1.0) + r() * 0.2, d = 30 + r() * 8;
    place(outer, KK.med[['rock_single_A', 'rock_single_B', 'rock_single_C', 'rock_single_D', 'rock_single_E'][i % 5]], 16 + r() * 12, Math.cos(a) * d, Math.sin(a) * d * 0.8 - 4, r() * 6);
  }
  for (const [x, z, s] of [[-30, 18, 18], [32, 16, 20], [-36, -10, 22], [36, -12, 20]]) place(outer, KK.med.rock_single_C, s, x, z, r() * 6);
  // пещера: тёмный вход, обращённый вперёд (+Z)
  const cave = sph(1, mCave, 0, 2, 16, 12, 8); cave.scale.set(15, 17, 8); g.add(cave);
  const lip = cyl(17, 18, 3, mRockD, 0, 2, 17, 14); lip.scale.z = 0.5; g.add(lip);
  // сокровища у входа и кости
  for (let i = 0; i < 14; i++) { const a = r() * 6.28, d = r() * 10; const o = mesh(new THREE.OctahedronGeometry(1.4 + r() * 1.4), mGold, Math.cos(a) * d + 4, 3.5 + r() * 2.5, 26 + Math.sin(a) * d * 0.5); o.rotation.set(r() * 3, r() * 3, 0); g.add(o); }
  const pile = cone(7, 5, mGold, 3, 5, 26, 10); g.add(pile);
  for (let i = 0; i < 6; i++) { const b = cyl(0.9, 0.9, 9 + r() * 5, mBone, -20 + r() * 40, 3.6, 24 + r() * 14, 5); b.rotation.z = Math.PI / 2; b.rotation.y = r() * 3; g.add(b); }
  const skull = sph(3.2, mBone, -18, 5, 30, 8, 6); g.add(skull);
  // знамя команды на скале
  g.add(cyl(0.9, 1.1, 40, mWood, 22, 30, -8, 6));
  const flag = new THREE.Mesh(new THREE.PlaneGeometry(16, 10, 4, 2), mFlag); flag.position.set(30.5, 44, -8); flag.castShadow = true; g.add(flag);
  if (typeof shadows === 'function') shadows(outer);
  return outer;
}

if (typeof KK_BLD !== 'undefined') KK_BLD.lair = kkLair;
