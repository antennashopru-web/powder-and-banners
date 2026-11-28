/* Новые войска: гренадер, драгун, мортира. Свои модели на фигурах KayKit (CC0) и процедурной геометрии.
   «Вперёд» — +X. Кадры те же, что у остальных: idle, walk (i, ph), act (i = 0..2), dead. */
'use strict';

// шапка гренадера: высокая митра цвета команды с медным налобником
function grenCap(team) {
  const g = new THREE.Group();
  const c = cone(0.42, 1.25, mat(TEAMS[team].main), 0, 0.62, 0, 10); c.scale.set(1, 1, 0.8); g.add(c);
  const plate = box(0.06, 0.9, 0.62, mat(0xd8b050, { metalness: 0.7, roughness: 0.35 }), 0.34, 0.45, 0); plate.rotation.z = -0.22; g.add(plate);
  g.add(sph(0.12, mat(0xf0e0c0), 0, 1.25, 0, 8, 6));
  g.add(cyl(0.45, 0.45, 0.12, mat(0x1c1a1c), 0, 0.06, 0, 12));
  return shadows(g);
}
function grenade() {
  const g = new THREE.Group();
  g.add(sph(0.24, mat(0x1e1e22, { metalness: 0.4, roughness: 0.5 }), 0, 0, 0, 10, 8));
  g.add(cyl(0.03, 0.03, 0.22, mat(0xc8a060), 0, 0.3, 0, 4));
  g.add(sph(0.06, mat(0xffa040, { emissive: 0xff7000 }), 0, 0.42, 0, 6, 4));
  return shadows(g);
}
function kkGrenadier(team, fr) {
  const rig = charRig('Knight', team, ['Knight_Cape']);
  const cap = grenCap(team); cap.position.set(0, 0.5, 0); rig.bones.head.add(cap);
  const gr = grenade(); gr.position.set(0, 0.1, 0); rig.bones.handslotr.add(gr);
  // сумка с гранатами на боку
  const bag = box(0.34, 0.3, 0.16, mat(0x6a4426), 0, 0, 0); bag.position.set(-0.35, 0.75, 0.3); rig.root.add(shadows(bag));
  if (fr.kind === 'walk') pose(rig, 'Walking_A', fr.i / 4);
  else if (fr.kind === 'act') pose(rig, 'Throw', [0.25, 0.42, 0.6][fr.i]);
  else if (fr.kind === 'dead') DEATH(rig);
  else pose(rig, 'Idle', 0);
  return wrapChar(rig);
}

function kkDragoon(team, fr) {
  const T = TEAMS[team];
  const { g, body } = horse(team ? 0x7a5a3a : 0x3a2a1c, T.main, (fr.i || 0) * Math.PI / 2, fr.kind === 'walk');
  const rig = charRig('Rogue', team, ['Rogue_Cape']);
  attachAlong(rig, 'handslotr', '2H_Crossbow', musketMesh(), 0);
  const hat = tricorne(team); hat.position.set(0, 0.62, 0); rig.bones.head.add(hat);
  pose(rig, 'Sit_Chair_Pose', 0);
  const legQ = {};
  for (const n in rig.bones) if (/leg|foot|toe|hips/i.test(n)) legQ[n] = rig.bones[n].quaternion.clone();
  const legP = rig.bones.hips ? rig.bones.hips.position.clone() : null;
  if (fr.kind === 'act') pose(rig, fr.i === 1 ? '2H_Ranged_Shoot' : '2H_Ranged_Aiming', fr.i === 1 ? 0.25 : 0.5);
  else pose(rig, '2H_Ranged_Aiming', 0.5);
  for (const n in legQ) rig.bones[n].quaternion.copy(legQ[n]);
  if (legP) rig.bones.hips.position.copy(legP);
  rig.root.updateMatrixWorld(true);
  const w = wrapChar(rig); w.position.set(-1, 6.5, 0); w.scale.setScalar(0.68); body.add(w);
  return g;
}

// мортира: короткий толстый ствол на санях, задран вверх; рядом бомбардир
function buildMortar(team, frame) {
  const T = TEAMS[team];
  const g = new THREE.Group(), wood = mat(0x6e4a2a, { map: TX.planks }), iron = mat(0x2e2e30, { metalness: 0.6, roughness: 0.5 });
  const bronze = mat(0xa07a40, { metalness: 0.85, roughness: 0.32 });
  const sled = new THREE.Group(); g.add(sled);
  for (const z of [-4, 4]) { sled.add(box(15, 3, 1.6, wood, 0, 2, z)); const r = box(15.5, 0.6, 1.8, iron, 0, 0.4, z); sled.add(r); }
  sled.add(box(4, 2, 9.6, wood, -4, 3.5, 0)); sled.add(box(3, 2, 9.6, wood, 5, 3.5, 0));
  sled.add(box(1.6, 1.6, 9.4, mat(T.main), 1, 2, 0));
  const rec = frame.kind === 'act' && frame.i === 1 ? -1.6 : 0;
  const barrel = new THREE.Group(); barrel.position.set(rec, 7, 0); barrel.rotation.z = frame.kind === 'act' ? 0.95 : 0.8; sled.add(barrel);
  const bm = cyl(3.6, 4.4, 10, bronze, 0, 3, 0, 14); barrel.add(bm);
  barrel.add(cyl(2.6, 2.6, 0.5, mat(0x111111), 0, 8.1, 0, 12));            // жерло
  for (const y of [7.4, 3, -0.8]) { const r = mesh(new THREE.TorusGeometry(y > 7 ? 3.7 : 4.4, 0.5, 6, 14), bronze, 0, y, 0); r.rotation.x = Math.PI / 2; barrel.add(r); }
  barrel.add(sph(4.2, bronze, 0, -1.6, 0, 12, 8));
  for (const z of [-4.6, 4.6]) { const t = cyl(1, 1, 1.6, iron, 0, 0, z, 8); t.rotation.x = Math.PI / 2; barrel.add(t); }
  // ядра рядом
  for (const [x, z] of [[-9, 6], [-10.5, 4.5], [-8.5, 3.6]]) g.add(sph(1.5, iron, x, 1.5, z, 8, 6));
  return g;
}
function kkMortar(team, fr) {
  const g = buildMortar(team, fr);
  const rig = charRig('Barbarian', team, ['Barbarian_Hat']);
  if (fr.kind === 'act') pose(rig, 'Interact', [0.2, 0.5, 0.8][fr.i]);
  else if (fr.kind === 'walk') pose(rig, 'Walking_A', fr.i / 4);
  else pose(rig, 'Idle', 0);
  const w = wrapChar(rig); w.position.set(-14, 0, -8); w.scale.setScalar(0.95); g.add(w);
  return g;
}

// герой «Генерал»: рыцарь без шлема, шляпа с пышным пером цвета команды, шпага и плащ
function plumedHat(team) {
  const g = tricorne(team);
  const pl = sph(0.22, mat(TEAMS[team].light), 0.1, 0.32, 0.18, 10, 8); pl.scale.set(1.6, 0.8, 0.9); g.add(pl);
  const pl2 = sph(0.16, mat(0xf4f0e8), -0.12, 0.3, 0.22, 8, 6); pl2.scale.set(1.5, 0.8, 0.9); g.add(pl2);
  return shadows(g);
}
function kkHero(team, fr) {
  const rig = charRig('Knight', team, ['Knight_Cape', '1H_Sword']);
  const hat = plumedHat(team); hat.position.set(0, 0.96, 0); hat.scale.setScalar(1.2); rig.bones.head.add(hat);
  if (fr.kind === 'walk') pose(rig, 'Walking_A', fr.i / 4);
  else if (fr.kind === 'act') pose(rig, '1H_Melee_Attack_Slice_Diagonal', [0.25, 0.42, 0.6][fr.i]);
  else if (fr.kind === 'dead') DEATH(rig);
  else pose(rig, 'Idle', 0);
  const w = wrapChar(rig); w.scale.multiplyScalar(1.15);
  return w;
}
