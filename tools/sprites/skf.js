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
    if (m.material && m.material.metalness > 0.5 && !m.material.userData.dark) { m.material = m.material.clone(); m.material.color.multiplyScalar(0.3); m.material.metalness = 0.3; m.material.userData.dark = true; }
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
  const { g } = skfFit(o, SKF_ROT.cannon, 32);
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
const SKF_ROT = { cannon: 0, mortar: 0, horse: Math.PI / 2 };

// ---------------------------------------------------------------- конь (скелет и анимация бега автора модели)
const HORSE_BODY = /Object_13/, HORSE_SADDLE = /Object_17/, HORSE_DROP = /Object_21/;
function horse(color, cloth, ph, gallop) {
  const o = skfClone('horse', (m) => !HORSE_DROP.test(m.name));
  const gl = SKF.horse;
  // кадр анимации: бег для движения, первый кадр шага для покоя
  const clip = gl.animations.find((a) => a.name.endsWith(gallop ? '|Run' : '|Walk')) || gl.animations[0];
  const mixer = new THREE.AnimationMixer(o); mixer.clipAction(clip).play();
  const t = gallop ? clip.duration * (((ph / (Math.PI * 2)) % 1) + 1) % 1 : 0;
  mixer.setTime(t);
  o.traverse((m) => {
    if (!m.isMesh) return;
    if (HORSE_BODY.test(m.name)) {
      // масть: светлее или темнее исходной гнедой, по яркости заданного цвета
      const c = new THREE.Color(color), l = (c.r + c.g + c.b) / 3, k = Math.max(0.45, Math.min(1.4, l / 0.3));
      m.material = m.material.clone(); m.material.color.multiplyScalar(k);
    }
    if (HORSE_SADDLE.test(m.name)) { m.material = m.material.clone(); m.material.color = new THREE.Color(cloth).convertSRGBToLinear(); m.material.map = null; }
  });
  // размер считаем по позе покоя, чтобы конь не «прыгал» между кадрами
  if (!SKF.horseFit) {
    const p = skfClone('horse', (m) => !HORSE_DROP.test(m.name));
    const mx = new THREE.AnimationMixer(p); mx.clipAction(gl.animations.find((a) => a.name.endsWith('|Walk')) || gl.animations[0]).play(); mx.setTime(0);
    const inner = new THREE.Group(); inner.rotation.y = SKF_ROT.horse; inner.add(p);
    const bb = skfBox(inner), sz = bb.getSize(new THREE.Vector3()), c = bb.getCenter(new THREE.Vector3());
    let sb = new THREE.Box3(); p.traverse((m) => { if (m.isMesh && HORSE_SADDLE.test(m.name)) sb = new THREE.Box3().setFromObject(m, true); });
    SKF.horseFit = { k: 34 / sz.x, c, minY: bb.min.y, saddle: sb.getCenter(new THREE.Vector3()), saddleTop: sb.max.y };
  }
  const F = SKF.horseFit;
  const inner = new THREE.Group(); inner.rotation.y = SKF_ROT.horse; inner.add(o);
  inner.position.set(-F.c.x, -F.minY, -F.c.z);
  const body = new THREE.Group(); body.scale.setScalar(F.k); body.add(inner);
  const g = new THREE.Group(); g.add(body);
  // всадника (body.add) ставим в седло: внешняя группа без масштаба
  const seat = new THREE.Group();
  seat.position.set((F.saddle.x - F.c.x) * F.k + 0.4, (F.saddleTop - F.minY) * F.k - 17.9, (F.saddle.z - F.c.z) * F.k);
  g.add(seat);
  return { g, body: seat };
}

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
