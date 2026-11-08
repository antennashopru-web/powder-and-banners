const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
(async () => {
  const out = process.argv[2] || 'kkout'; const only = process.argv[3] ? process.argv[3].split(',') : null;
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage(); p.on('pageerror', e => console.log('ERR', e.message)); p.on('console', m => { if (m.type() === 'error' || m.type()==='warning') console.log('C', m.text().slice(0,200)); });
  await p.goto('http://localhost:8765/kk.html'); await p.waitForFunction(() => window.READY, null, { timeout: 30000 });
  const t0 = Date.now();
  const res = await p.evaluate(async (o) => { try { return await window.kkRenderAll(o); } catch (e) { return { err: e.stack }; } }, only);
  if (res.err) { console.log(res.err); process.exit(1); }
  console.log('render ms', Date.now() - t0);
  const mf = path.join(out, 'sprites.json'); const meta = fs.existsSync(mf) ? JSON.parse(fs.readFileSync(mf)) : { units: {}, blds: {} };
  const save = (name, o) => { fs.writeFileSync(path.join(out, name + '.png'), Buffer.from(o.url.split(',')[1], 'base64')); return o.meta; };
  for (const k in res.units) meta.units[k] = save('u_' + k, res.units[k]);
  for (const k in res.blds) meta.blds[k] = save('b_' + k, res.blds[k]);
  if (res.trees) { meta.trees = save('trees', res.trees); meta.rocks = save('rocks', res.rocks); }
  fs.writeFileSync(mf, JSON.stringify(meta)); console.log(JSON.stringify(meta).slice(0, 600));
  await b.close();
})();
