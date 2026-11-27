// node bake3d-run.js <out-dir>   (нужен http-сервер на 8765 в этой папке, см. README)
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
(async () => {
  const out = process.argv[2] || 'out3d'; fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage(); p.on('pageerror', (e) => console.log('ERR', e.message)); p.on('console', (m) => { if (m.type() === 'error') console.log('C', m.text().slice(0, 300)); });
  await p.goto('http://localhost:8765/bake3d.html'); await p.waitForFunction(() => window.READY, null, { timeout: 30000 });
  const r = await p.evaluate(async () => { try { return await window.bakeAll(); } catch (e) { return { err: e.stack }; } });
  if (r.err) { console.log(r.err); process.exit(1); }
  fs.writeFileSync(path.join(out, 'models.json'), JSON.stringify(r.index));
  fs.writeFileSync(path.join(out, 'models.bin'), Buffer.from(r.bin, 'base64'));
  for (const k in r.index.models) console.log(k, r.index.models[k].n, 'verts', r.index.models[k].tris, 'tris x', r.index.models[k].frames);
  console.log('ms', r.ms, 'bin', fs.statSync(path.join(out, 'models.bin')).size);
  await b.close();
})();
