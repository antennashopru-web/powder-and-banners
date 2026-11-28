/* Реалистичные солдаты: модель «European Soldier 17th Century» (HaiPham, Sketchfab, CC BY 4.0).
   Модель статичная (без скелета): здесь ей ставится свой простой скелет, вершины привязываются
   к ближайшим костям, а позы (шаг, прицел, удар пикой, рубка, посадка в седло, гибель) задаются
   направлениями костей. Мундир перекрашивается в цвет команды, оружие и шапки — свои.
   Подключается после skf.js и переопределяет kkPeasant, kkPike, kkMusket, kkGrenadier, kkHero,
   gunner, rider, kkCav, kkDragoon. */
'use strict';
SKF_LIST.push('soldier');

// суставы в долях роста (модель смотрит вдоль +Z, левая рука на +X)
const SJ = {
  root: [0, 0.52, 0], spine: [0, 0.62, 0], chest: [0, 0.72, 0], neck: [0, 0.82, 0], head: [0, 0.87, 0], top: [0, 1.0, 0],
  shL: [0.12, 0.79, 0], elL: [0.235, 0.705, 0], wrL: [0.33, 0.62, 0], tipL: [0.37, 0.53, 0],
  hipL: [0.075, 0.50, 0], knL: [0.095, 0.27, 0], anL: [0.10, 0.055, -0.005], toeL: [0.10, 0.01, 0.07],
};
for (const k of Object.keys(SJ)) if (k.endsWith('L')) { const v = SJ[k]; SJ[k.slice(0, -1) + 'R'] = [-v[0], v[1], v[2]]; }
const SBONES = [ // имя, родитель
  ['root', null], ['spine', 'root'], ['chest', 'spine'], ['neck', 'chest'], ['head', 'neck'],
  ['shL', 'chest'], ['elL', 'shL'], ['wrL', 'elL'], ['shR', 'chest'], ['elR', 'shR'], ['wrR', 'elR'],
  ['hipL', 'root'], ['knL', 'hipL'], ['anL', 'knL'], ['hipR', 'root'], ['knR', 'hipR'], ['anR', 'knR'],
];
// отрезки для привязки вершин: [от, до, кость, сторона (0 — середина, 1 — левая, -1 — правая)]
const SSEG = [
  ['root', 'spine', 'root', 0], ['spine', 'chest', 'spine', 0], ['chest', 'neck', 'chest', 0], ['neck', 'head', 'neck', 0], ['head', 'top', 'head', 0],
  ['shL', 'elL', 'shL', 1], ['elL', 'wrL', 'elL', 1], ['wrL', 'tipL', 'wrL', 1],
  ['shR', 'elR', 'shR', -1], ['elR', 'wrR', 'elR', -1], ['wrR', 'tipR', 'wrR', -1],
  ['hipL', 'knL', 'hipL', 1], ['knL', 'anL', 'knL', 1], ['anL', 'toeL', 'anL', 1],
  ['hipR', 'knR', 'hipR', -1], ['knR', 'anR', 'knR', -1], ['anR', 'toeR', 'anR', -1],
];
const SV = (k) => new THREE.Vector3(...SJ[k]);
let SOLDIER = null;

function segDist(p, a, b) {
  const ab = b.clone().sub(a), t = Math.max(0, Math.min(1, p.clone().sub(a).dot(ab) / ab.lengthSq()));
  return p.distanceTo(a.clone().addScaledVector(ab, t));
}
function hsvOf(r, g, b) {
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
  let h = 0; if (d > 0) h = mx === r ? ((g - b) / d) % 6 : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return [(h * 60 + 360) % 360, mx ? d / mx : 0, mx];
}

function soldierPrep() {
  if (SOLDIER) return SOLDIER;
  const src = SKF.soldier.scene; src.updateMatrixWorld(true);
  const bb = new THREE.Box3().setFromObject(src, true), H = bb.max.y - bb.min.y;
  const cx = (bb.min.x + bb.max.x) / 2, cz = (bb.min.z + bb.max.z) / 2;
  const parts = [];
  const bi = Object.fromEntries(SBONES.map(([n], i) => [n, i]));
  src.traverse((o) => {
    if (!o.isMesh) return;
    let geo = o.geometry.clone(); geo.applyMatrix4(o.matrixWorld);
    geo.translate(-cx, -bb.min.y, -cz); geo.scale(1 / H, 1 / H, 1 / H);
    for (const n of Object.keys(geo.attributes)) if (n !== 'position' && n !== 'uv') geo.deleteAttribute(n);
    // упрощение сетки: телефону столько треугольников на каждого солдата не нужно
    const tris = (geo.index ? geo.index.count : geo.attributes.position.count) / 3;
    const target = /Eyes/.test(o.name) ? 120 : /Outfit_aiOutfit/.test(o.name) ? 2600 : /Head/.test(o.name) ? 700 : 500;
    if (tris > target * 1.2) {
      geo = BufferGeometryUtils.mergeVertices(geo, 1e-5);
      geo = new SimplifyModifier().modify(geo, Math.floor(geo.attributes.position.count * (1 - target / tris)));
    }
    if (!geo.index) geo = BufferGeometryUtils.mergeVertices(geo, 1e-5);
    geo.computeVertexNormals();      // плавные нормали для пререндера спрайтов
    geo = geo.toNonIndexed();
    // текстура: зелёное сукно мундира делаем серым, а цвет даёт вершина (цвет команды)
    const map = o.material.map, img = map.image;
    const cv = document.createElement('canvas'); cv.width = img.width; cv.height = img.height;
    const g2 = cv.getContext('2d'); g2.drawImage(img, 0, 0);
    const id = g2.getImageData(0, 0, cv.width, cv.height), px = id.data;
    const isCoat = (r, g, b) => { const [h, s, v] = hsvOf(r, g, b); return h > 65 && h < 175 && s > 0.18 && v > 0.08; };
    const pos = geo.attributes.position, uv = geo.attributes.uv, n = pos.count;
    const coat = new Float32Array(n);
    for (let i = 0; i < n; i++) {
      const u = uv.getX(i) - Math.floor(uv.getX(i)), v = uv.getY(i) - Math.floor(uv.getY(i));
      const x = Math.min(cv.width - 1, Math.floor(u * cv.width)), y = Math.min(cv.height - 1, Math.floor((map.flipY ? 1 - v : v) * cv.height));
      const k = (y * cv.width + x) * 4; coat[i] = isCoat(px[k], px[k + 1], px[k + 2]) ? 1 : 0;
    }
    for (let k = 0; k < px.length; k += 4) if (isCoat(px[k], px[k + 1], px[k + 2])) {
      const l = Math.min(255, (0.3 * px[k] + 0.59 * px[k + 1] + 0.11 * px[k + 2]) * 2.3); px[k] = px[k + 1] = px[k + 2] = l;
    }
    g2.putImageData(id, 0, 0);
    const tex = new THREE.CanvasTexture(cv); tex.flipY = map.flipY; tex.colorSpace = THREE.SRGBColorSpace;
    // привязка к костям: две ближайшие кости (с учётом стороны), веса обратно квадрату расстояния
    const si = new Uint16Array(n * 4), sw = new Float32Array(n * 4), p = new THREE.Vector3();
    const segs = SSEG.map(([a, b, bone, side]) => ({ a: SV(a), b: SV(b), bone: bi[bone], side }));
    for (let i = 0; i < n; i++) {
      p.fromBufferAttribute(pos, i);
      const d = segs.map((s) => {
        let dd = segDist(p, s.a, s.b);
        if (s.side && Math.sign(p.x || 1e-6) !== s.side && Math.abs(p.x) > 0.01) dd += 0.3;
        return [dd, s.bone];
      }).sort((x, y) => x[0] - y[0]);
      const w0 = 1 / Math.pow(d[0][0] + 0.004, 3), w1 = d[1][1] === d[0][1] ? 0 : 1 / Math.pow(d[1][0] + 0.004, 3);
      si[i * 4] = d[0][1]; si[i * 4 + 1] = d[1][1]; sw[i * 4] = w0 / (w0 + w1); sw[i * 4 + 1] = w1 / (w0 + w1);
    }
    geo.setAttribute('skinIndex', new THREE.Uint16BufferAttribute(si, 4));
    geo.setAttribute('skinWeight', new THREE.Float32BufferAttribute(sw, 4));
    parts.push({ name: o.name, geo, coat, tex });
  });
  SOLDIER = { parts, geoCache: new Map() };
  return SOLDIER;
}

// цвет мундира в вершинах (сукно — coatCol, остальное — белый); кэш по цвету
function soldierGeo(part, coatCol) {
  const key = part.name + coatCol;
  let g = SOLDIER.geoCache.get(key);
  if (g) return g;
  g = part.geo.clone();
  const c = new THREE.Color(coatCol), n = g.attributes.position.count, col = new Float32Array(n * 3);
  // цвет вершины хранится линейным, bake3d переводит цвет материала в sRGB, а вершинный множит как есть
  for (let i = 0; i < n; i++) { const k = part.coat[i]; col[i * 3] = k ? c.r : 1; col[i * 3 + 1] = k ? c.g : 1; col[i * 3 + 2] = k ? c.b : 1; }
  g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
  SOLDIER.geoCache.set(key, g);
  return g;
}

// новый скелет + сетки; возвращает { root, bones }
function soldierRig(coatCol) {
  soldierPrep();
  const root = new THREE.Group(), bones = {};
  for (const [n, par] of SBONES) {
    const b = new THREE.Bone(); b.name = n;
    const pv = SV(n); if (par) pv.sub(SV(par));
    b.position.copy(pv); bones[n] = b;
    (par ? bones[par] : root).add(b);
  }
  root.updateMatrixWorld(true);
  const skel = new THREE.Skeleton(SBONES.map(([n]) => bones[n]));
  for (const part of SOLDIER.parts) {
    const m = new THREE.SkinnedMesh(soldierGeo(part, coatCol), new THREE.MeshStandardMaterial({ map: part.tex, vertexColors: true, roughness: 0.85, metalness: 0.05 }));
    m.userData.triAvg = true; m.castShadow = true; m.receiveShadow = true; m.frustumCulled = false;
    root.add(m); m.bind(skel, new THREE.Matrix4());
  }
  return { root, bones };
}

// ---------------------------------------------------------------- позы
const V3 = (x, y, z) => new THREE.Vector3(x, y, z).normalize();
const _q = new THREE.Quaternion();
// повернуть кость так, чтобы её отрезок к ребёнку смотрел в мировом направлении dir
function aimBone(r, name, child, dir) {
  const b = r.bones[name]; r.root.updateMatrixWorld(true);
  const rest = SV(child).sub(SV(name)).normalize();
  b.parent.getWorldQuaternion(_q); _q.invert();
  const local = dir.clone().applyQuaternion(_q);
  b.quaternion.setFromUnitVectors(rest, local);
}
// конечности: { armL: [плечо, предплечье], armR, legL: [бедро, голень], legR }
function soldierPose(r, P) {
  for (const n in r.bones) r.bones[n].quaternion.identity();
  if (P.lean) r.bones.spine.rotation.x = P.lean;
  if (P.twist) r.bones.chest.rotation.y = P.twist;
  if (P.headX) r.bones.head.rotation.x = P.headX;
  const L = (k, a, b, c, dirs) => { if (!dirs) return; aimBone(r, a, b, dirs[0]); aimBone(r, b, c, dirs[1]); };
  L('legL', 'hipL', 'knL', 'anL', P.legL); L('legR', 'hipR', 'knR', 'anR', P.legR);
  L('armL', 'shL', 'elL', 'wrL', P.armL); L('armR', 'shR', 'elR', 'wrR', P.armR);
  r.root.updateMatrixWorld(true);
}
// две кости руки/ноги дотягиваются до точки t (в координатах модели); hint — куда сгибать локоть
function reachArm(side, t, hint) {
  const S = SV('sh' + side), E = SV('el' + side), W = SV('wr' + side);
  const a = E.distanceTo(S), b = W.distanceTo(E);
  const d = t.clone().sub(S), L = Math.min(d.length(), (a + b) * 0.999), dn = d.clone().normalize();
  const x = (a * a - b * b + L * L) / (2 * L), h = Math.sqrt(Math.max(0, a * a - x * x));
  const side2 = hint.clone().sub(dn.clone().multiplyScalar(hint.dot(dn))).normalize();
  const elbow = S.clone().addScaledVector(dn, x).addScaledVector(side2, h);
  const wrist = S.clone().addScaledVector(dn, L);
  return [elbow.clone().sub(S).normalize(), wrist.sub(elbow).normalize()];
}
const T3 = (x, y, z) => new THREE.Vector3(x, y, z);
const ARM_DOWN = (s) => [V3(0.18 * s, -1, 0.04), V3(0.1 * s, -1, 0.22)];
const LEG_STAND = (s) => [V3(0.03 * s, -1, 0.0), V3(0.0, -1, -0.02)];
function walkLegs(ph, amp) {
  const leg = (p) => { const a = Math.sin(p) * amp, k = Math.max(0, Math.sin(p + 1.3)) * 0.9; return [V3(0, -Math.cos(a), Math.sin(a)), V3(0, -Math.cos(a - k), Math.sin(a - k))]; };
  return { legL: leg(ph), legR: leg(ph + Math.PI) };
}
function poseFor(kind, fr) {
  const ph = fr.ph || ((fr.i || 0) * Math.PI / 2);
  const P = { armL: ARM_DOWN(1), armR: ARM_DOWN(-1), legL: LEG_STAND(1), legR: LEG_STAND(-1) };
  if (fr.kind === 'walk') {
    Object.assign(P, walkLegs(ph, 0.42));
    const sw = Math.sin(ph) * 0.45;
    P.armL = [V3(0.15, -Math.cos(sw), -Math.sin(sw)), V3(0.1, -Math.cos(sw) * 0.9, -Math.sin(sw) + 0.35)];
    P.armR = [V3(-0.15, -Math.cos(-sw), -Math.sin(-sw)), V3(-0.1, -Math.cos(-sw) * 0.9, -Math.sin(-sw) + 0.35)];
  }
  return P;
}

// ---------------------------------------------------------------- оружие и снаряжение (в долях роста)
const WS = 1 / 2.4;     // предметы KayKit-размера → доли роста
function inRoot(r, obj, pos, dir, up) {
  // поставить предмет в точку pos (мировую), ось Y предмета — вдоль dir
  const g = new THREE.Group(); g.add(obj); g.position.copy(pos);
  g.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.clone().normalize());
  if (up) g.rotateY(up);
  r.root.add(g); return g;
}
function handPos(r, side) { r.root.updateMatrixWorld(true); const w = r.bones['wr' + side], p = new THREE.Vector3(); w.getWorldPosition(p); const e = r.bones['el' + side].getWorldPosition(new THREE.Vector3()); return p.addScaledVector(p.clone().sub(e).normalize(), 0.035); }
function musket() { const m = musketMesh(); m.scale.setScalar(WS * 0.95); return m; }
function pike() { const m = pikeMesh(6.8); m.position.y = -1.6; const g = new THREE.Group(); g.add(m); g.scale.setScalar(WS); return g; }
function blade(len) {
  const g = new THREE.Group();
  g.add(box(0.014, len, 0.004, METAL(), 0, len / 2 + 0.03, 0));
  g.add(box(0.07, 0.008, 0.012, mat(0xc8a050, { metalness: 0.7, roughness: 0.3 }), 0, 0.03, 0));
  g.add(cyl(0.008, 0.008, 0.05, mat(0x3a2414), 0, 0.0, 0, 5));
  return shadows(g);
}
function axe() {
  const g = new THREE.Group();
  g.add(cyl(0.01, 0.012, 0.36, mat(0x7a5432), 0, 0.12, 0, 5));
  g.add(box(0.012, 0.06, 0.07, METAL(), 0, 0.28, 0.03));
  return shadows(g);
}
function hatTop(r) { r.root.updateMatrixWorld(true); return r.bones.head.getWorldPosition(new THREE.Vector3()).add(new THREE.Vector3(0, 0.115, 0.0)); }
function addHead(r, obj, dy, s) { const p = hatTop(r); obj.position.set(0, (dy || 0), 0); obj.scale.setScalar(s); const g = new THREE.Group(); g.add(obj); r.bones.head.add(g); g.position.set(0, 0.115 + (dy || 0), 0); obj.position.set(0, 0, 0); return g; }
function sash(r, color) {
  // перевязь через плечо: тонкий тор на груди
  const t = mesh(new THREE.TorusGeometry(0.085, 0.009, 5, 20), mat(color, { roughness: 0.75 }));
  t.scale.set(1, 1.25, 0.62); t.rotation.z = 0.55; t.position.set(0, 0.0, 0.004);
  const g = new THREE.Group(); g.add(shadows(t)); g.position.set(0, 0.055, 0); r.bones.chest.add(g); return g;
}

// обёртка: рост в игре ~24 (как фигуры KayKit), «вперёд» — +X
const S_H = 23.5;
function wrapSoldier(r, k) { const g = new THREE.Group(); r.root.rotation.y = Math.PI / 2; r.root.scale.setScalar(S_H * (k || 1)); g.add(r.root); return g; }
function deadPose(r) {
  soldierPose(r, { armL: [V3(1, 0.1, 0.1), V3(1, 0.3, 0.2)], armR: [V3(-1, -0.2, 0.3), V3(-0.6, -0.2, 0.8)], legL: [V3(0.15, -1, 0.05), V3(0.1, -1, 0)], legR: [V3(-0.1, -1, 0.1), V3(-0.1, -1, 0.2)] });
  // лечь на спину: поворот всего тела
  const w = new THREE.Group(); w.add(r.root); r.root.rotation.x = -Math.PI / 2; r.root.position.set(0, 0.05, 0.45);
  return w;
}
function finish(r, fr, k) {
  if (fr.kind === 'dead') { const w = deadPose(r); const g = new THREE.Group(); w.rotation.y = Math.PI / 2; w.scale.setScalar(S_H * (k || 1)); g.add(w); return g; }
  return wrapSoldier(r, k);
}
const TEAM_COAT = (team) => TEAMS[team].main;

// ---------------------------------------------------------------- рода войск
// мушкет: приклад в точке butt, ствол вдоль dir; руки берутся за ложе в двух местах
function holdMusket(r, P, butt, dir, gripR, gripL) {
  P.armR = reachArm('R', butt.clone().addScaledVector(dir, gripR), T3(-1, -1, -0.2));
  P.armL = reachArm('L', butt.clone().addScaledVector(dir, gripL), T3(0.6, -1, 0));
}
function placeMusket(r, butt, dir) { inRoot(r, musket(), butt.clone().addScaledVector(dir, 0.325 * 0.95 / 0.95), dir); }
const AIM = { butt: T3(-0.075, 0.765, 0.05), dir: V3(0.04, 0.02, 1) };
const PORT = { butt: T3(-0.085, 0.47, 0.11), dir: V3(0.38, 1, 0.12) };
function kkMusket(team, fr) {
  const r = soldierRig(TEAM_COAT(team));
  const P = poseFor('musket', fr);
  const M = (fr.kind === 'act' || fr.kind === 'idle') ? AIM : PORT;
  if (fr.kind !== 'dead') {
    if (M === AIM) { P.twist = -0.12; P.headX = 0.1; if (fr.kind === 'act' && fr.i === 1) P.lean = -0.05; holdMusket(r, P, M.butt, M.dir, 0.1, 0.36); }
    else holdMusket(r, P, M.butt, M.dir, 0.06, 0.3);
  }
  soldierPose(r, P);
  if (fr.kind === 'dead') placeMusket(r, T3(0.2, 0.08, 0.07), V3(0, 1, 0)); else placeMusket(r, M.butt, M.dir);
  addHead(r, plume(TEAMS[team].main, 0.6, 0.2), 0.0, 0.11).rotation.y = Math.PI / 2;
  return finish(r, fr);
}
function kkPike(team, fr) {
  const r = soldierRig(TEAM_COAT(team));
  const P = poseFor('pike', fr);
  let hold = null, dir, at;
  if (fr.kind === 'act') {
    const e = [0.0, 0.18, 0.34][fr.i];
    P.lean = 0.12 + e * 0.3; P.legL = [V3(0.02, -0.92, 0.4), V3(0, -1, 0.1)]; P.legR = [V3(-0.02, -0.95, -0.3), V3(0, -1, -0.45)];
    dir = V3(0, -0.04, 1);
    const hr = T3(-0.1, 0.6, 0.02 + e), hl = T3(0.0, 0.62, 0.3 + e);
    P.armR = reachArm('R', hr, T3(-1, -0.6, -0.3)); P.armL = reachArm('L', hl, T3(0.8, -1, 0));
    at = hr; hold = 1.2;
  } else {
    dir = V3(0.02, 1, 0.04); at = T3(-0.14, 0.56, 0.12);
    P.armR = reachArm('R', at, T3(-0.6, -1, -0.4)); hold = 0.67;
  }
  soldierPose(r, P);
  // пика: древко длиной 2.8 роста, за руку держат на расстоянии hold от торца
  if (fr.kind === 'act') { const hr = handPos(r, 'R'), hl = handPos(r, 'L'); dir = hl.clone().sub(hr).normalize(); dir.y = Math.max(-0.1, Math.min(0.1, dir.y)); dir.normalize(); at = hr; }
  else if (hold !== null) at = handPos(r, 'R');
  if (fr.kind === 'dead') { at = T3(-0.2, 0.75, 0.07); dir = V3(0, 1, 0); }
  if (hold !== null) inRoot(r, pike(), at.clone().addScaledVector(dir, -hold + 0.67), dir);
  sash(r, TEAMS[team].main);
  return finish(r, fr);
}
function kkPeasant(team, fr) {
  const r = soldierRig(0x7a5a3a);
  const P = poseFor('peasant', fr);
  let dir = V3(0, -0.2, 1);
  if (fr.kind === 'act') {
    const a = [-0.6, 0.6, 1.4][fr.i];   // замах — удар
    P.armR = [V3(-0.25, Math.cos(a) * 0.9, Math.sin(a) * 0.4 + 0.3), V3(-0.1, Math.cos(a + 0.6), Math.sin(a + 0.6) + 0.2)];
    P.lean = [-0.05, 0.1, 0.25][fr.i];
    dir = V3(0, Math.cos(a + 1.2), Math.sin(a + 1.2));
  }
  soldierPose(r, P);
  if (fr.kind === 'dead') inRoot(r, axe(), T3(0.22, 0.3, 0.06), V3(0, 1, 0)); else inRoot(r, axe(), handPos(r, 'R'), dir);
  sash(r, TEAMS[team].main);
  return finish(r, fr);
}
function kkGrenadier(team, fr) {
  const r = soldierRig(TEAM_COAT(team));
  const P = poseFor('gren', fr);
  if (fr.kind === 'act') {
    const a = [-1.2, 0.2, 1.3][fr.i];
    P.armR = [V3(-0.3, Math.cos(a), -Math.sin(a) * -0.8 - 0.2), V3(-0.1, Math.cos(a + 0.4), Math.sin(a + 0.4))];
    P.twist = [0.35, 0.0, -0.3][fr.i];
  }
  soldierPose(r, P);
  { const gr = grenade(); gr.scale.setScalar(WS * 0.5); const g = new THREE.Group(); g.add(gr); g.position.copy(fr.kind === 'dead' ? T3(0.25, 0.5, 0.08) : handPos(r, 'R')); r.root.add(g); }
  sash(r, 0x6a4426);
  return finish(r, fr);
}
function kkHero(team, fr) {
  const r = soldierRig(0x2a2a30);
  const P = poseFor('hero', fr);
  let dir = V3(0, -0.6, 1);
  if (fr.kind === 'act') {
    const a = [-0.8, 0.5, 1.4][fr.i];
    P.armR = [V3(-0.3, Math.cos(a) * 0.8, Math.sin(a) * 0.5 + 0.3), V3(-0.05, Math.cos(a + 0.5), Math.sin(a + 0.5) + 0.3)];
    dir = V3(0, Math.cos(a + 1.0), Math.sin(a + 1.0));
  } else if (fr.kind === 'idle') { P.armR = [V3(-0.2, -0.9, 0.3), V3(0, -0.4, 1)]; dir = V3(0, 0.3, 1); }
  soldierPose(r, P);
  if (fr.kind === 'dead') inRoot(r, blade(0.34), T3(0.22, 0.3, 0.06), V3(0, 1, 0)); else inRoot(r, blade(0.34), handPos(r, 'R'), dir);
  sash(r, 0xe8c050);
  const p1 = plume(0xf4f0e8, 0.8, 0.2), p2 = plume(TEAMS[team].main, 0.6, 0.2); p2.position.set(0, 0.05, 0.2);
  const pg = new THREE.Group(); pg.add(p1); pg.add(p2); addHead(r, pg, 0.0, 0.12).rotation.y = Math.PI / 2;
  return finish(r, fr, 1.06);
}
function gunner(team, fr) {
  const r = soldierRig(0x6a5038);
  const P = poseFor('gunner', fr);
  if (fr.kind === 'act') { const e = [0.2, 0.6, 0.9][fr.i]; P.armR = [V3(-0.2, -0.6, 0.7), V3(0, -0.3 + e * 0.5, 1)]; P.armL = [V3(0.2, -0.6, 0.7), V3(0, -0.3 + e * 0.5, 1)]; P.lean = 0.3; }
  soldierPose(r, P);
  sash(r, TEAMS[team].main);
  return finish(r, fr, 0.98);
}
// всадник: сидит, ноги из позы посадки, руки из позы действия
function soldierRider(team, coat, arms, weapon) {
  const r = soldierRig(coat);
  const P = { legL: [V3(0.35, -0.25, 0.9), V3(0.12, -1, -0.1)], legR: [V3(-0.35, -0.25, 0.9), V3(-0.12, -1, -0.1)], armL: [V3(0.1, -0.7, 0.7), V3(0, -0.2, 1)], armR: ARM_DOWN(-1) };
  Object.assign(P, arms);
  soldierPose(r, P);
  if (weapon) weapon(r);
  // сиденье: таз модели (0.50 роста) на седле; в старой посадке всадник ставился на 9.4 над группой седла
  const g = new THREE.Group(); r.root.rotation.y = Math.PI / 2; r.root.scale.setScalar(S_H * 0.95); r.root.position.y = 9.4 + 8.5 - S_H * 0.95 * 0.47; g.add(r.root);
  return g;
}
function kkCav(team, fr) {
  const T = TEAMS[team];
  const { g, body } = horse(team ? 0x6a4a32 : 0x4f3524, T.main, (fr.i || 0) * Math.PI / 2, fr.kind === 'walk');
  let arms = {}, dir = V3(0, 0.6, 1);
  if (fr.kind === 'act') { const a = [-0.9, 0.4, 1.3][fr.i]; arms.armR = [V3(-0.35, Math.cos(a) * 0.8, Math.sin(a) * 0.5 + 0.3), V3(-0.1, Math.cos(a + 0.5), Math.sin(a + 0.5) + 0.3)]; dir = V3(-0.1, Math.cos(a + 1.0), Math.sin(a + 1.0)); }
  else arms.armR = [V3(-0.25, -0.8, 0.4), V3(-0.05, 0.2, 1)];
  body.add(soldierRider(team, T.main, arms, (r) => { inRoot(r, blade(0.4), handPos(r, 'R'), dir); sash(r, T.main); }));
  return g;
}
function kkDragoon(team, fr) {
  const T = TEAMS[team];
  const { g, body } = horse(team ? 0x8a6a48 : 0x3a2a1c, T.main, (fr.i || 0) * Math.PI / 2, fr.kind === 'walk');
  const arms = { armR: reachArm('R', AIM.butt.clone().addScaledVector(AIM.dir, 0.1), T3(-1, -1, -0.2)), armL: reachArm('L', AIM.butt.clone().addScaledVector(AIM.dir, 0.36), T3(0.6, -1, 0)) };
  body.add(soldierRider(team, 0x3a4a5a, arms, (r) => {
    placeMusket(r, AIM.butt, AIM.dir);
    addHead(r, plume(T.main, 0.6, 0.2), 0, 0.11).rotation.y = Math.PI / 2;
  }));
  return g;
}
