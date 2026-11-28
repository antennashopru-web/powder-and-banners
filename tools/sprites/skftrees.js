/* Реалистичные деревья со Sketchfab (CC BY 4.0, авторы — в SKETCHFAB.md и в игре):
   ель, лиственное дерево и берёзы. Листва — плоскости с прозрачной текстурой; чтобы лес
   не был слишком тяжёлым, часть веток убирается (прореживание по связным кускам сетки). */
'use strict';
SKF_LIST.push('pine', 'leaf', 'birch');

// оставить долю p связных кусков сетки (ветка = отдельный кусок)
function thinMesh(geo, p, seed) {
  const g = BufferGeometryUtils.mergeVertices(geo.clone(), 1e-4), idx = g.index.array, n = g.attributes.position.count;
  const par = new Int32Array(n); for (let i = 0; i < n; i++) par[i] = i;
  const find = (a) => { while (par[a] !== a) { par[a] = par[par[a]]; a = par[a]; } return a; };
  for (let t = 0; t < idx.length; t += 3) { const a = find(idx[t]); par[find(idx[t + 1])] = a; par[find(idx[t + 2])] = a; }
  // крупные куски (ствол с сучьями) остаются всегда
  const size = new Map(); for (let t = 0; t < idx.length; t += 3) { const c = find(idx[t]); size.set(c, (size.get(c) || 0) + 1); }
  const r = rng(seed), keep = new Map(), out = [];
  for (let t = 0; t < idx.length; t += 3) {
    const c = find(idx[t]); if (!keep.has(c)) keep.set(c, size.get(c) > 40 || r() < p);
    if (keep.get(c)) out.push(idx[t], idx[t + 1], idx[t + 2]);
  }
  g.setIndex(out); return g;
}
// листва: вырезание по альфе и в 2D (three.js), и в игре (атлас PNG)
function leafy(o, isLeaf, p, seed, gain) {
  const baking = typeof bakeFrame === 'function';
  o.traverse((m) => {
    if (!m.isMesh) return;
    m.material = m.material.clone(); m.material.metalness = 0; m.material.roughness = 0.9;
    if (m.material.map) { m.material.map.userData.alpha = true; m.material.map.userData.gain = gain || 1; }
    if (!baking) m.material.color.multiplyScalar(Math.min(gain || 1, 1.25));
    if (!isLeaf(m)) { m.material.transparent = false; m.material.alphaTest = 0; return; }
    m.material.transparent = false; m.material.alphaTest = 0.4; m.material.depthWrite = true; m.material.side = THREE.DoubleSide;
    m.customDepthMaterial = new THREE.MeshDepthMaterial({ depthPacking: THREE.RGBADepthPacking, map: m.material.map, alphaTest: 0.4 });
    if (p < 1) m.geometry = thinMesh(m.geometry, p, seed);
  });
  return o;
}
function skfPine(p, seed) { return leafy(skfClone('pine'), (m) => /Branch/i.test(m.name), p, seed, 1.2); }
function skfLeaf(p, seed) { return leafy(skfClone('leaf'), () => true, p, seed, 1.5); }
function skfBirch(i) { return leafy(skfClone('birch', (m) => i < 0 || m.name.includes('_T' + (i + 1) + '_')), () => true, 1, 0, 1.9); }
// дерево высотой h (в игре), повёрнутое на ry, в точке x, z
function treeAt(g, o, h, x, z, ry) {
  const w = new THREE.Group(); w.rotation.y = ry || 0; w.add(o); w.updateMatrixWorld(true);
  const bb = skfBox(w), k = h / (bb.max.y - bb.min.y), c = bb.getCenter(new THREE.Vector3());
  const s = new THREE.Group(); s.scale.set(k, k / KY, k); w.position.set(-c.x, -bb.min.y, -c.z); s.add(w);
  s.position.set(x, 0, z); g.add(s); return s;
}
// 0..3 хвойные, 4..7 лиственные: одиночные деревья и купы по 2–3 дерева
function kkTree(k) {
  const g = new THREE.Group();
  if (k === 0) treeAt(g, skfPine(0.42, 11), 46, 0, 0, 0);
  else if (k === 1) treeAt(g, skfPine(0.42, 12), 38, 0, 0, 2.1);
  else if (k === 2) { treeAt(g, skfPine(0.26, 13), 44, -7, -3, 0.5); treeAt(g, skfPine(0.26, 14), 34, 8, 5, 3.3); }
  else if (k === 3) { treeAt(g, skfPine(0.2, 15), 48, 0, -8, 1.2); treeAt(g, skfPine(0.2, 16), 38, -10, 6, 4.0); treeAt(g, skfPine(0.2, 17), 33, 10, 7, 5.5); }
  else if (k === 4) treeAt(g, skfLeaf(0.6, 21), 40, 0, 0, 0);
  else if (k === 5) treeAt(g, skfBirch(0), 36, 0, 0, 0);
  else if (k === 6) treeAt(g, skfBirch(-1), 38, 0, 0, 0.6);
  else { treeAt(g, skfLeaf(0.4, 22), 42, -6, -4, 2.2); treeAt(g, skfBirch(2), 34, 11, 7, 0); treeAt(g, skfBirch(1), 30, -2, 11, 1.5); }
  return g;
}
