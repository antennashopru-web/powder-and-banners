/* Модели со Sketchfab под лицензией CC BY 4.0 (авторы перечислены в SKETCHFAB.md и в игре, «Авторы»).
   Подключается после uniforms.js: пушка, мортира, конь, дом, башня и мельница.
   Модели скачиваются в skf/ скриптом fetch-sketchfab.py (нужен свой токен Sketchfab). */
'use strict';
const SKF = {};
const SKF_LIST = ['cannon', 'mortar', 'horse', 'house', 'tower', 'mill'];
async function skfLoad() {
  for (const n of SKF_LIST) if (!SKF[n]) SKF[n] = await loadGLTF('skf/' + n + '.glb');
}
{ const base = kkLoad; kkLoad = async function () { await base(); await skfLoad(); }; }

// копия сцены (со скелетом), только нужные меши; отметка для усреднения цвета по треугольнику
function skfClone(name, keep) {
  const src = SKF[name].scene, o = SkeletonUtils.clone(src);
  o.traverse((m) => {
    if (!m.isMesh) return;
    m.userData.triAvg = true; m.castShadow = true; m.receiveShadow = true; m.frustumCulled = false;
    // металл без отражений в игре выглядел бы белёсым: затемняем
    // (с текстурой — это и есть цвет поверхности, металлом его не считаем: без отражений он чернеет)
    if (m.material && m.material.metalness > 0.5 && !m.material.userData.dark) {
      m.material = m.material.clone(); m.material.userData.dark = true;
      if (!m.material.map) m.material.color.multiplyScalar(0.3);
      m.material.metalness = m.material.map ? 0.15 : 0.3; m.material.roughness = Math.max(0.6, m.material.roughness);
    }
    if (keep && !keep(m)) m.visible = false;
  });
  return o;
}
function skfBox(o) { o.updateMatrixWorld(true); const b = new THREE.Box3(); o.traverse((m) => { if (m.isMesh && m.visible) b.union(new THREE.Box3().setFromObject(m, true)); }); return b; }
// поставить на землю: длина вдоль X = len, центр по X/Z в нуле
function skfFit(o, rotY, len, ky) {
  const inner = new THREE.Group(); inner.rotation.y = rotY; inner.add(o);
  const g0 = new THREE.Group(); g0.add(inner);
  const bb = skfBox(g0), sz = bb.getSize(new THREE.Vector3()), c = bb.getCenter(new THREE.Vector3());
  const k = len / sz.x;
  inner.position.set(-c.x, -bb.min.y, -c.z);
  const g = new THREE.Group(); g.add(g0); g0.scale.set(k, k / (ky || 1), k);
  return { g, k, size: sz.clone().multiplyScalar(k) };
}

// ---------------------------------------------------------------- пушка и мортира
function teamBand(team, w, h, d, x, y, z) { return box(w, h, d, mat(TEAMS[team].main), x, y, z); }
function buildCannon(team, frame) {
  const o = skfClone('cannon');
  const { g } = skfFit(o, SKF_ROT.cannon, 36);
  const rec = frame.kind === 'act' && frame.i === 1 ? -2.5 : 0;
  g.position.x = rec;
  const w = new THREE.Group(); w.add(g);
  w.add(teamBand(team, 1.4, 1.4, 6.6, -12.5 + rec, 3.2, 0));
  return w;
}
function buildMortar(team, frame) {
  const o = skfClone('mortar');
  const { g } = skfFit(o, SKF_ROT.mortar, 20);
  const rec = frame.kind === 'act' && frame.i === 1 ? -1.6 : 0;
  g.position.x = rec;
  const w = new THREE.Group(); w.add(g);
  w.add(teamBand(team, 1.6, 1.4, 9.4, -8 + rec, 1.4, 0));
  const iron = mat(0x2e2e30, { metalness: 0.6, roughness: 0.5 });
  for (const [x, z] of [[-11, 7], [-12.5, 5.5], [-10.5, 4.6]]) w.add(sph(1.5, iron, x, 1.5, z, 8, 6));
  return w;
}
const SKF_ROT = { cannon: Math.PI, mortar: 0 };

// ---------------------------------------------------------------- конь (скелет и анимация автора модели)
// «Horse» (MAXDESIGN-3D): одна длинная запись движений; галоп — 0…0.7 с, стойка — около 16.9 с
const HORSE_GALLOP = [0, 0.70], HORSE_IDLE = 16.9;
function horsePose(o, t) {
  const mixer = new THREE.AnimationMixer(o); mixer.clipAction(SKF.horse.animations[0]).play(); mixer.setTime(t);
  o.updateMatrixWorld(true);
}
function horse(color, cloth, ph, gallop) {
  const o = skfClone('horse');
  // масть: темнее или светлее исходной гнедой, по яркости заданного цвета
  const c = new THREE.Color(color), l = (c.r + c.g + c.b) / 3, kc = Math.max(0.55, Math.min(1.35, l / 0.3));
  o.traverse((m) => {
    if (!m.isMesh) return;
    m.material = m.material.clone(); m.material.color.multiplyScalar(kc);
    // грива и хвост — полупрозрачные пряди; в игре без прозрачности, поэтому просто тёмные
    if (/Object_65/.test(m.name)) { m.material.map = null; m.material.color.setHex(0x1c140e); }
  });
  horsePose(o, gallop ? HORSE_GALLOP[0] + (HORSE_GALLOP[1] - HORSE_GALLOP[0]) * ((((ph / (Math.PI * 2)) % 1) + 1) % 1) : HORSE_IDLE);
  // поворот и размер считаем один раз по стойке, чтобы конь не «прыгал» между кадрами
  if (!SKF.horseFit) {
    const p = skfClone('horse'); horsePose(p, HORSE_IDLE);
    const bone = (n) => { let r = null; p.traverse((x) => { if (x.isBone && x.name.startsWith(n)) r = r || x; }); return r.getWorldPosition(new THREE.Vector3()); };
    const head = bone('Head_'), hips = bone('Hips_'), sp = bone('Spine2_');
    const rot = Math.atan2(head.z - hips.z, head.x - hips.x);
    const inner = new THREE.Group(); inner.rotation.y = rot; inner.add(p);
    const bb = skfBox(inner), sz = bb.getSize(new THREE.Vector3()), cc = bb.getCenter(new THREE.Vector3());
    const spw = sp.clone().applyAxisAngle(new THREE.Vector3(0, 1, 0), rot);
    SKF.horseFit = { rot, k: 34 / sz.x, c: cc, minY: bb.min.y, maxY: bb.max.y, sp: spw };
  }
  const F = SKF.horseFit;
  const inner = new THREE.Group(); inner.rotation.y = F.rot; inner.add(o);
  inner.position.set(-F.c.x, -F.minY, -F.c.z);
  const sc = new THREE.Group(); sc.scale.setScalar(F.k); sc.add(inner);
  const g = new THREE.Group(); g.add(sc);
  // седло и чепрак цвета команды поверх спины
  const sx = (F.sp.x - F.c.x) * F.k, back = (F.sp.y - F.minY) * F.k + SKF_SADDLE.dy;
  const body = new THREE.Group(); body.position.set(sx + SKF_SADDLE.dx, 0, 0); g.add(body);
  const cl = mat(cloth, { map: TX.cloth }), trim = mat(0xe8c870, { metalness: 0.4, roughness: 0.45 });
  const W = SKF_SADDLE.w;
  for (const z of [W, -W]) {
    const sd = box(7.4, 4.4, 0.3, cl, 0, back - 2.4, z); sd.rotation.x = z > 0 ? -0.18 : 0.18; body.add(sd);
    const tr = box(7.6, 0.4, 0.35, trim, 0, back - 4.6, z * 1.06); tr.rotation.x = sd.rotation.x; body.add(tr);
  }
  body.add(box(7.4, 0.45, W * 2, cl, 0, back, 0));
  body.add(box(4.8, 1.1, W * 1.5, mat(0x4a2c16, { roughness: 0.6 }), 0, back + 0.7, 0));
  body.add(box(1.0, 1.3, W * 1.3, mat(0x3a2210), 2.2, back + 1.2, 0));
  body.add(box(0.9, 0.9, W * 1.3, mat(0x3a2210), -2.4, back + 1.1, 0));
  // всадник (body.add в kkCav) садится на высоту седла: смещение под старую посадку (седло на 17.9)
  const seat = new THREE.Group(); seat.position.set(0.4, back + 1.0 - 17.9, 0); body.add(seat);
  return { g, body: seat };
}
const SKF_SADDLE = { dx: 0, dy: 0, w: 2.9 };

// ---------------------------------------------------------------- здания: дом, башня, мельница на ферме
KK_BLD.house = (t) => yard(t, fitModel(skfClone('house'), 54, 50), 64, [['barrel', 6, -26, 25], ['sack', 6, -20, 27], ['resource_lumber', 12, 24, -24, 1.2], ['bucket_water', 4, 25, 25], ['flag', 10, -27, -8]]);
KK_BLD.tower = (t) => yard(t, fitModel(skfClone('tower'), 32, 32), 64, [['barrel', 6, 26, 26], ['crate_A_small', 5, -27, 27, 0.6], ['ladder', 10, -26, -24, 0.8], ['flag', 12, 25, -20]]);
{
  const farm = KK_BLD.farm;
  KK_BLD.farm = (t) => {
    const g = farm(t);
    // мельница KayKit — последний крупный объект с x=24, z=-34: заменяем на мельницу со Sketchfab
    const old = g.children.find((o) => Math.abs(o.position.x - 24) < 0.01 && Math.abs(o.position.z + 34) < 0.01);
    if (old) g.remove(old);
    place(g, skfClone('mill'), 44, 24, -34);
    return g;
  };
}
function kkMortar(team, fr) {
  const g = buildMortar(team, fr);
  const w = gunner(team, fr); w.position.set(-17, 0, -11); w.scale.setScalar(0.95); g.add(w);
  return g;
}
