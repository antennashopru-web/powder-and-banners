/* Убранство земли для 3D: трава, цветы, кусты, камешки, кувшинки и горы вокруг карты.
   Камни, кувшинки, горы и холмы — CC0-модели KayKit Medieval Hexagon; трава, цветы и кусты — свои. */
'use strict';
function tuft(seed, col) {
  const g = new THREE.Group(), r = rng(seed), m = mat(col, { roughness: 1 });
  for (let i = 0; i < 5; i++) {
    const a = r() * 6.28, d = r() * 2.4, h = 3 + r() * 3;
    const c = cone(0.55, h, m, Math.cos(a) * d, h / 2, Math.sin(a) * d, 3);
    c.rotation.z = (r() - 0.5) * 0.6; c.rotation.x = (r() - 0.5) * 0.6; g.add(c);
  }
  return g;
}
function flowers(seed, col) {
  const g = new THREE.Group(), r = rng(seed), stem = mat(0x4f8a34, { roughness: 1 }), pet = mat(col, { roughness: 0.8 }), mid = mat(0xffd84a);
  for (let i = 0; i < 6; i++) {
    const a = r() * 6.28, d = 1 + r() * 3.2, h = 2.4 + r() * 2.2, x = Math.cos(a) * d, z = Math.sin(a) * d;
    g.add(cyl(0.18, 0.18, h, stem, x, h / 2, z, 3));
    const p = sph(0.9, pet, x, h + 0.2, z, 5, 3); p.scale.y = 0.5; g.add(p);
    g.add(sph(0.35, mid, x, h + 0.55, z, 4, 3));
  }
  g.add(tuft(seed + 7, 0x5f9a3c));
  return g;
}
function bush(seed, big) {
  const g = new THREE.Group(), r = rng(seed);
  const cols = [0x3f7a2e, 0x4a8a34, 0x356a28];
  const n = big ? 6 : 4, R = big ? 5.5 : 4;
  for (let i = 0; i < n; i++) {
    const a = r() * 6.28, d = r() * R * 0.6, s = R * (0.55 + r() * 0.35);
    const b = mesh(new THREE.IcosahedronGeometry(s, 0), mat(cols[i % 3], { roughness: 0.95, flatShading: true }), Math.cos(a) * d, s * 0.75, Math.sin(a) * d);
    b.rotation.set(r() * 3, r() * 3, r() * 3); g.add(b);
  }
  if (big) for (let i = 0; i < 5; i++) { const a = r() * 6.28; g.add(sph(0.6, mat(0xd8343a), Math.cos(a) * R * 0.8, R * (0.6 + r() * 0.5), Math.sin(a) * R * 0.8, 4, 3)); }  // ягоды
  return g;
}
function pebbles(seed) {
  const g = new THREE.Group(), r = rng(seed), rs = ['rock_single_A', 'rock_single_B', 'rock_single_C', 'rock_single_D', 'rock_single_E'];
  for (let i = 0; i < 3; i++) place(g, KK.med[rs[(seed + i) % 5]], 4 + r() * 5, (r() - 0.5) * 10, (r() - 0.5) * 10, r() * 6);
  return g;
}
function lily(seed) {
  const g = new THREE.Group();
  place(g, KK.med[seed % 2 ? 'waterlily_A' : 'waterlily_B'], 16, 0, 0, seed);
  place(g, KK.med['waterplant_' + 'ABC'[seed % 3]], 10, 8, 6, seed * 2);
  g.position.y = -1.2;
  return g;
}
// горы и холмы за краем карты: большие, их видно издалека
function bigNature(name, w) { const o = fitModel(KK.med[name], w, w, 1); return o; }
// земля за краем карты: плоская плита тёмной травы
function edgeGround() {
  const g = new THREE.Group();
  const p = box(64, 2, 64, mat(0x4f7a34, { roughness: 1 }), 0, -1.2, 0); p.receiveShadow = true; g.add(p);
  return g;
}
// таблица убранства: номер = индекс (важен порядок, как в Models3D.DECO)
const KYW = (o) => { const w = new THREE.Group(); o.scale.y /= KY; w.add(o); return w; };   // игра растягивает всё по Y на KY
const DECO = [
  () => KYW(tuft(1, 0x5f9a3c)), () => KYW(tuft(2, 0x6fa844)), () => KYW(tuft(3, 0x88a64a)),
  () => KYW(flowers(4, 0xf4f0f0)), () => KYW(flowers(5, 0xf0d040)), () => KYW(flowers(6, 0xd85a8a)), () => KYW(flowers(7, 0x8a7ae0)),
  () => KYW(bush(8, false)), () => KYW(bush(9, true)), () => pebbles(10), () => pebbles(13),
  () => lily(0), () => lily(1),
  () => bigNature('mountain_A_grass_trees', 150), () => bigNature('mountain_B_grass_trees', 170), () => bigNature('mountain_C_grass', 140),
  () => bigNature('hills_A_trees', 120), () => bigNature('hills_B_trees', 120), () => bigNature('hills_C_trees', 110),
  () => KYW(edgeGround()),
];
